package com.anass.billing.api.auth;

import com.anass.billing.common.user.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Deletes refresh tokens that expired more than the retention period ago. Safe to run on several instances.
 */
@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository repository;
    private final Duration retention;

    public RefreshTokenCleanup(RefreshTokenRepository repository,
                               @Value("${app.auth.refresh-token-retention:1d}") Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    @Scheduled(cron = "0 30 3 * * *")   // every night at 03:30
    public void deleteExpired() {
        int deleted = repository.deleteExpiredBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}