package com.taskflow.service;

import com.taskflow.dto.RegisterForm;
import com.taskflow.entity.User;
import com.taskflow.exception.DuplicateValueException;
import com.taskflow.repository.EmailCodeRepository;
import com.taskflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CategoryService categoryService;
    private final EmailCodeRepository emailCodes;
    private final AccountSessions accountSessions;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, CategoryService categoryService,
                       EmailCodeRepository emailCodes, AccountSessions accountSessions) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.categoryService = categoryService;
        this.emailCodes = emailCodes;
        this.accountSessions = accountSessions;
    }

    public User register(RegisterForm form) {
        String email = normalizeEmail(form.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateValueException("Этот email уже зарегистрирован");
        }

        User user = new User();
        user.setUsername(form.getUsername().strip());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setEmailVerified(false);
        userRepository.saveAndFlush(user);
        categoryService.createDefaults(user);
        return user;
    }

    @Transactional(readOnly = true)
    public User byEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));
    }

    public void updateName(String email, String name) {
        byEmail(email).setUsername(name.strip());
    }

    public boolean changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmailForUpdate(email)
            .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }

        replacePassword(user, newPassword);
        return true;
    }

    void replacePassword(User user, String newPassword) {
        user.setPassword(passwordEncoder.encode(newPassword));
        emailCodes.findByUserId(user.getId()).forEach(code -> code.invalidate());
        accountSessions.expireAfterCommit(user.getEmail());
    }

    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
