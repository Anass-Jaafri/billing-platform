package com.anass.billing.api.auth;

import com.anass.billing.common.user.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefreshTokenCleanupTest {

    @Test
    void deleteExpired_removesRowsOlderThanTheRetentionPeriod() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.deleteExpiredBefore(any())).thenReturn(3);
        RefreshTokenCleanup cleanup = new RefreshTokenCleanup(repository, Duration.ofDays(1));

        Instant before = Instant.now();
        cleanup.deleteExpired();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minus(Duration.ofDays(1)), after.minus(Duration.ofDays(1)));
    }
}