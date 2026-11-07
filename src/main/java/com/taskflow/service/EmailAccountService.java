package com.taskflow.service;

import com.taskflow.dto.RegisterForm;
import com.taskflow.entity.EmailCode;
import com.taskflow.entity.EmailCode.Purpose;
import com.taskflow.entity.User;
import com.taskflow.exception.EmailDeliveryException;
import com.taskflow.repository.EmailCodeRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class EmailAccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CODE_FORMAT = Pattern.compile("[0-9]{8}");

    private final UserService users;
    private final UserRepository userRepository;
    private final EmailCodeRepository emailCodes;
    private final PasswordEncoder passwordEncoder;
    private final AccountMail mail;
    private final Clock clock;
    private final TaskExecutor mailExecutor;
    private final TransactionTemplate transactions;

    EmailAccountService(UserService users, UserRepository userRepository, EmailCodeRepository emailCodes,
                        PasswordEncoder passwordEncoder, AccountMail mail, Clock clock,
                        @Qualifier("accountMailExecutor") TaskExecutor mailExecutor,
                        PlatformTransactionManager transactionManager) {
        this.users = users;
        this.userRepository = userRepository;
        this.emailCodes = emailCodes;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
        this.clock = clock;
        this.mailExecutor = mailExecutor;
        transactions = new TransactionTemplate(transactionManager);
    }

    public boolean isMailConfigured() {
        return mail.isConfigured();
    }

    @Transactional
    public User register(RegisterForm form) {
        if (!isMailConfigured()) {
            throw new EmailDeliveryException();
        }
        User user = users.register(form);
        sendCode(user, Purpose.CONFIRM_EMAIL);
        return user;
    }

    public void resendConfirmation(String email) {
        queueCode(email, Purpose.CONFIRM_EMAIL);
    }

    public void requestPasswordReset(String email) {
        queueCode(email, Purpose.RESET_PASSWORD);
    }

    @Transactional
    public boolean confirmEmail(String email, String code) {
        User user = lockAccount(email).orElse(null);
        if (user == null || !user.requiresEmailConfirmation() || !consume(user, Purpose.CONFIRM_EMAIL, code)) {
            return false;
        }
        user.setEmailVerified(true);
        return true;
    }

    @Transactional
    public boolean resetPassword(String email, String code, String newPassword) {
        User user = lockAccount(email).orElse(null);
        if (user == null || !consume(user, Purpose.RESET_PASSWORD, code)) {
            return false;
        }
        user.setEmailVerified(true);
        users.replacePassword(user, newPassword);
        return true;
    }

    private void queueCode(String email, Purpose purpose) {
        if (!isMailConfigured() || email == null || email.isBlank()) {
            return;
        }
        try {
            mailExecutor.execute(() -> issueRequestedCode(email, purpose));
        } catch (TaskRejectedException busy) {
            // Keep the public response independent of whether an email is queued.
        }
    }

    private void issueRequestedCode(String email, Purpose purpose) {
        try {
            transactions.executeWithoutResult(status -> lockAccount(email)
                .filter(user -> purpose == Purpose.RESET_PASSWORD || user.requiresEmailConfirmation())
                .ifPresent(user -> sendCode(user, purpose)));
        } catch (EmailDeliveryException deliveryFailed) {
            // Roll back the new code and retain any previous one if delivery fails.
        }
    }

    private Optional<User> lockAccount(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailForUpdate(UserService.normalizeEmail(email));
    }

    private void sendCode(User user, Purpose purpose) {
        Instant now = clock.instant();
        EmailCode challenge = emailCodes.findByUserIdAndPurpose(user.getId(), purpose)
            .orElseGet(() -> new EmailCode(user, purpose));
        if (!challenge.canSend(now)) {
            return;
        }

        String code = String.format(Locale.ROOT, "%08d", RANDOM.nextInt(100_000_000));
        challenge.issue(passwordEncoder.encode(code), now);
        emailCodes.saveAndFlush(challenge);
        mail.sendCode(user.getEmail(), purpose, code);
    }

    private boolean consume(User user, Purpose purpose, String code) {
        if (code == null || !CODE_FORMAT.matcher(code).matches()) {
            return false;
        }
        EmailCode challenge = emailCodes.findByUserIdAndPurpose(user.getId(), purpose).orElse(null);
        if (challenge == null || !challenge.canCheck(clock.instant())) {
            return false;
        }
        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.recordFailedAttempt();
            return false;
        }
        challenge.invalidate();
        return true;
    }
}
