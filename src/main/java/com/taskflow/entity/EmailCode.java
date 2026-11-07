package com.taskflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "email_codes", uniqueConstraints = @UniqueConstraint(
    name = "uk_email_code_user_purpose", columnNames = {"user_id", "purpose"}))
public class EmailCode {
    public enum Purpose { CONFIRM_EMAIL, RESET_PASSWORD }

    public static final int VALID_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS_PER_HOUR = 5;
    private static final Duration RESEND_DELAY = Duration.ofMinutes(1);
    private static final Duration SEND_WINDOW = Duration.ofHours(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(length = 60)
    private String codeHash;

    private Instant expiresAt;
    private Instant lastSentAt;
    private Instant sendWindowStartedAt;
    private int sendsInWindow;
    private int failedAttempts;

    protected EmailCode() {
    }

    public EmailCode(User user, Purpose purpose) {
        this.user = user;
        this.purpose = purpose;
    }

    public boolean canSend(Instant now) {
        if (lastSentAt != null && now.isBefore(lastSentAt.plus(RESEND_DELAY))) {
            return false;
        }
        return sendWindowExpired(now) || sendsInWindow < MAX_SENDS_PER_HOUR;
    }

    public void issue(String codeHash, Instant now) {
        if (sendWindowExpired(now)) {
            sendWindowStartedAt = now;
            sendsInWindow = 0;
        }
        this.codeHash = codeHash;
        expiresAt = now.plus(Duration.ofMinutes(VALID_MINUTES));
        lastSentAt = now;
        sendsInWindow++;
        failedAttempts = 0;
    }

    private boolean sendWindowExpired(Instant now) {
        return sendWindowStartedAt == null || !now.isBefore(sendWindowStartedAt.plus(SEND_WINDOW));
    }

    public boolean canCheck(Instant now) {
        return codeHash != null && failedAttempts < MAX_ATTEMPTS && now.isBefore(expiresAt);
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void recordFailedAttempt() {
        failedAttempts++;
        if (failedAttempts >= MAX_ATTEMPTS) {
            invalidate();
        }
    }

    public void invalidate() {
        codeHash = null;
        expiresAt = null;
    }
}
