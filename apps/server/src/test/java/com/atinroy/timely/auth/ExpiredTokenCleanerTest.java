package com.atinroy.timely.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredTokenCleanerTest {
    @Mock
    AuthTokenRepository tokens;

    @Test
    void deletesTokensThatExpiredBeforeNow() {
        ExpiredTokenCleaner cleaner = new ExpiredTokenCleaner(tokens);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

        Instant before = Instant.now();
        cleaner.cleanExpiredTokens();
        Instant after = Instant.now();

        verify(tokens).deleteExpired(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    @Test
    void doesNotSwallowRepositoryFailures() {
        when(tokens.deleteExpired(any(Instant.class))).thenThrow(new IllegalStateException("db down"));
        ExpiredTokenCleaner cleaner = new ExpiredTokenCleaner(tokens);

        assertThatThrownBy(cleaner::cleanExpiredTokens)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db down");
    }
}
