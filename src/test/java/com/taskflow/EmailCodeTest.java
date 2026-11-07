package com.taskflow;

import com.taskflow.entity.EmailCode;
import com.taskflow.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EmailCodeTest {
    private static final Instant NOW = Instant.parse("2026-11-07T08:00:00Z");

    @Test
    void codeExpiresAtFifteenMinutes() {
        EmailCode code = newChallenge();
        code.issue("hash", NOW);

        assertThat(code.canCheck(NOW.plusSeconds(899))).isTrue();
        assertThat(code.canCheck(NOW.plusSeconds(900))).isFalse();
    }

    @Test
    void fifthWrongAttemptInvalidatesTheCode() {
        EmailCode code = newChallenge();
        code.issue("hash", NOW);

        for (int attempt = 0; attempt < 4; attempt++) {
            code.recordFailedAttempt();
        }
        assertThat(code.canCheck(NOW)).isTrue();

        code.recordFailedAttempt();
        assertThat(code.canCheck(NOW)).isFalse();
        assertThat(code.getCodeHash()).isNull();
    }

    @Test
    void usedCodeDoesNotResetSendCooldownOrHourlyLimit() {
        EmailCode code = newChallenge();
        code.issue("first hash", NOW);
        code.invalidate();

        assertThat(code.canCheck(NOW)).isFalse();
        assertThat(code.canSend(NOW.plusSeconds(59))).isFalse();
        for (int minute = 1; minute < 5; minute++) {
            Instant sentAt = NOW.plusSeconds(minute * 60L);
            assertThat(code.canSend(sentAt)).isTrue();
            code.issue("renewed hash", sentAt);
        }

        assertThat(code.canSend(NOW.plusSeconds(3599))).isFalse();
        assertThat(code.canSend(NOW.plusSeconds(3600))).isTrue();
    }

    private static EmailCode newChallenge() {
        return new EmailCode(new User(), EmailCode.Purpose.RESET_PASSWORD);
    }
}
