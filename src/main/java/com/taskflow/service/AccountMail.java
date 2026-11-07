package com.taskflow.service;

import com.taskflow.entity.EmailCode;
import com.taskflow.entity.EmailCode.Purpose;
import com.taskflow.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class AccountMail {
    private static final Logger log = LoggerFactory.getLogger(AccountMail.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;
    private final boolean credentialsConfigured;

    AccountMail(ObjectProvider<JavaMailSender> mailSender, Environment config) {
        this.mailSender = mailSender;
        host = config.getProperty("spring.mail.host", "");
        from = config.getProperty("taskflow.mail.from", "");
        boolean authentication = config.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, true);
        credentialsConfigured = !authentication || (StringUtils.hasText(config.getProperty("spring.mail.username"))
            && StringUtils.hasText(config.getProperty("spring.mail.password")));
    }

    boolean isConfigured() {
        return StringUtils.hasText(host) && StringUtils.hasText(from)
            && credentialsConfigured && mailSender.getIfAvailable() != null;
    }

    void sendCode(String email, Purpose purpose, String code) {
        String action = purpose == Purpose.CONFIRM_EMAIL ? "Подтверждение почты" : "Восстановление пароля";
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("TaskFlow — " + action);
        message.setText("""
            Код TaskFlow: %s

            Введите его в TaskFlow в течение %d минут. Код действует один раз.
            Если вы не запрашивали письмо, проигнорируйте его. Никому не сообщайте код.
            """.formatted(code, EmailCode.VALID_MINUTES));

        try {
            mailSender.getObject().send(message);
        } catch (MailException failure) {
            log.warn("Could not send TaskFlow account email ({})", failure.getClass().getSimpleName());
            throw new EmailDeliveryException();
        }
    }
}
