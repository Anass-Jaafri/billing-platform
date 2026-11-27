package com.anass.billing.api.auth;

import com.anass.billing.api.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
class LoginAttemptLimiterIntegrationTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;

    @BeforeEach
    void connect() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
    }

    @AfterEach
    void disconnect() {
        connectionFactory.destroy();
    }

    private String uniqueUser() {
        return "user-" + UUID.randomUUID();   // every test gets keys of its own
    }

    private void fail(LoginAttemptLimiter limiter, String user, String address, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkAllowed(user, address);
            limiter.recordFailure(user, address);
        }
    }

    @Test
    void blocksAfterTheLimit_andSaysHowLongToWait() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(template, 3, Duration.ofMinutes(15));
        String user = uniqueUser();

        fail(limiter, user, "1.1.1.1", 3);

        assertThatThrownBy(() -> limiter.checkAllowed(user, "1.1.1.1"))
                .isInstanceOfSatisfying(TooManyLoginAttemptsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 900L));
    }

    @Test
    void otherAddressesAndOtherUsernames_areNotAffected() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(template, 3, Duration.ofMinutes(15));
        String user = uniqueUser();
        fail(limiter, user, "1.1.1.1", 3);

        assertThatCode(() -> limiter.checkAllowed(user, "2.2.2.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed(uniqueUser(), "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLogin_startsTheCountFromZero() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(template, 3, Duration.ofMinutes(15));
        String user = uniqueUser();
        fail(limiter, user, "1.1.1.1", 2);

        limiter.reset(user, "1.1.1.1");

        assertThatCode(() -> fail(limiter, user, "1.1.1.1", 3)).doesNotThrowAnyException();
    }

    @Test
    void theBlockEnds_whenTheWindowPasses() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(template, 1, Duration.ofSeconds(2));
        String user = uniqueUser();
        fail(limiter, user, "1.1.1.1", 1);
        assertThatThrownBy(() -> limiter.checkAllowed(user, "1.1.1.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        await().atMost(Duration.ofSeconds(6)).untilAsserted(() ->
                assertThatCode(() -> limiter.checkAllowed(user, "1.1.1.1")).doesNotThrowAnyException());
    }

    @Test
    void failsOpen_whenRedisIsUnreachable() {
        LettuceConnectionFactory deadFactory = new LettuceConnectionFactory("localhost", 1);   // nothing listens here
        deadFactory.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(deadFactory);
        deadTemplate.afterPropertiesSet();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(deadTemplate, 3, Duration.ofMinutes(15));

        try {
            assertThatCode(() -> {
                limiter.checkAllowed("anyone", "1.1.1.1");
                limiter.recordFailure("anyone", "1.1.1.1");
                limiter.reset("anyone", "1.1.1.1");
            }).doesNotThrowAnyException();
        } finally {
            deadFactory.destroy();
        }
    }
}