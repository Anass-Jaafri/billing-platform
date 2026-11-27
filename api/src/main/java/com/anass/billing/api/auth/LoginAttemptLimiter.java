package com.anass.billing.api.auth;

import com.anass.billing.api.exception.TooManyLoginAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Counts failed logins per username and client address in Redis. If Redis is unreachable
 * it lets the login through (and logs a warning) rather than locking everybody out.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);
    private static final String KEY_PREFIX = "login-failures:";

    // Add one; start the expiry timer on the first failure. One script = one indivisible step in Redis,
    // so a crash can never leave a counter that never expires.
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(StringRedisTemplate redis,
                               @Value("${app.auth.max-login-failures:5}") int maxFailures,
                               @Value("${app.auth.login-failure-window:15m}") Duration window) {
        this.redis = redis;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    // The username is hashed: a client controls its length and characters, and must not control key names.
    private static String key(String username, String clientAddress) {
        return KEY_PREFIX + clientAddress + ":" + sha256(username);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Throws if this username from this address has used up its attempts. Does not count the call.
     */
    public void checkAllowed(String username, String clientAddress) {
        String key = key(username, clientAddress);
        try {
            String failures = redis.opsForValue().get(key);
            if (failures != null && Long.parseLong(failures) >= maxFailures) {
                Long remaining = redis.getExpire(key, TimeUnit.SECONDS);
                throw new TooManyLoginAttemptsException(
                        remaining == null || remaining < 1 ? window.toSeconds() : remaining);
            }
        } catch (DataAccessException e) {
            log.warn("Login rate limit not checked, Redis unavailable: {}", e.getMessage());
        }
    }

    public void recordFailure(String username, String clientAddress) {
        try {
            Long count = redis.execute(INCREMENT_WITH_EXPIRY,
                    List.of(key(username, clientAddress)), String.valueOf(window.toSeconds()));
            if (count != null && count == maxFailures) {
                log.warn("Login blocked for {} minutes: {} failed attempts from address {}",
                        window.toMinutes(), maxFailures, clientAddress);
            }
        } catch (DataAccessException e) {
            log.warn("Failed login not counted, Redis unavailable: {}", e.getMessage());
        }
    }

    public void reset(String username, String clientAddress) {
        try {
            redis.delete(key(username, clientAddress));
        } catch (DataAccessException e) {
            log.warn("Login counter not cleared, Redis unavailable: {}", e.getMessage());
        }
    }
}