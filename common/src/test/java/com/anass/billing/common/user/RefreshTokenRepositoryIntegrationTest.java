package com.anass.billing.common.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RefreshTokenRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    RefreshTokenRepository repository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void connectToContainer(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private RefreshToken token(UUID family, Instant expiresAt) {
        UUID adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        return repository.saveAndFlush(RefreshToken.builder()
                .jti(UUID.randomUUID()).userId(adminId).familyId(family).expiresAt(expiresAt).build());
    }

    private Integer liveTokensIn(UUID family) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where family_id = ? and revoked_at is null",
                Integer.class, family);
    }

    @Test
    void aSavedToken_isFoundByItsJti_andStartsLive() {
        RefreshToken saved = token(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(7)));

        RefreshToken found = repository.findByJtiForUpdate(saved.getJti()).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.isRevoked()).isFalse();
    }

    @Test
    void revokeFamily_revokesOnlyTheLiveTokensOfThatFamily() {
        UUID familyA = UUID.randomUUID();
        UUID familyB = UUID.randomUUID();
        Instant later = Instant.now().plus(Duration.ofDays(7));
        RefreshToken alreadyUsed = token(familyA, later);
        alreadyUsed.revoke(Instant.now().minusSeconds(60));
        repository.saveAndFlush(alreadyUsed);
        token(familyA, later);
        token(familyB, later);

        int revoked = repository.revokeFamily(familyA, Instant.now());

        assertThat(revoked).isEqualTo(1);               // the one already revoked was left alone
        assertThat(liveTokensIn(familyA)).isZero();
        assertThat(liveTokensIn(familyB)).isEqualTo(1); // another session is unaffected
    }

    @Test
    void deleteExpiredBefore_removesOnlyRowsOlderThanTheCutoff() {
        UUID family = UUID.randomUUID();
        Instant now = Instant.now();
        token(family, now.minus(Duration.ofDays(3)));
        RefreshToken recentlyExpired = token(family, now.minus(Duration.ofHours(1)));
        RefreshToken live = token(family, now.plus(Duration.ofDays(7)));

        int deleted = repository.deleteExpiredBefore(now.minus(Duration.ofDays(1)));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findByJti(recentlyExpired.getJti())).isPresent();
        assertThat(repository.findByJti(live.getJti())).isPresent();
    }

    @Test
    void aJtiCanOnlyBeStoredOnce() {
        RefreshToken first = token(UUID.randomUUID(), Instant.now().plus(Duration.ofDays(7)));

        assertThatThrownBy(() -> repository.saveAndFlush(RefreshToken.builder()
                .jti(first.getJti()).userId(first.getUserId()).familyId(UUID.randomUUID())
                .expiresAt(first.getExpiresAt()).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}