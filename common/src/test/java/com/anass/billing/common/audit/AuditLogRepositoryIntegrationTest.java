package com.anass.billing.common.audit;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
public class AuditLogRepositoryIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private AuditLog entry(UUID eventId, UUID chargeId) {
        return AuditLog.forEvent(eventId, chargeId, "charge.confirmed", "{\"chargeId\":\"" + chargeId + "\"}");
    }

    @Test
    void save_forUnknownCharge_succeeds_becauseThereIsNoForeignKey() {
        UUID chargeId = UUID.randomUUID(); // no such charge exists

        auditLogRepository.saveAndFlush(entry(UUID.randomUUID(), chargeId));

        assertThat(auditLogRepository.findByChargeIdOrderByRecordedAtAsc(chargeId)).hasSize(1);
    }

    @Test
    void findByCharge_returnsAllEntriesForThatChargeOnly() {
        UUID chargeId = UUID.randomUUID();
        auditLogRepository.saveAndFlush(entry(UUID.randomUUID(), chargeId));
        auditLogRepository.saveAndFlush(entry(UUID.randomUUID(), chargeId));
        auditLogRepository.saveAndFlush(entry(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(auditLogRepository.findByChargeIdOrderByRecordedAtAsc(chargeId)).hasSize(2);
    }

    @Test
    void existsByEventId_reflectsWhatWasSaved() {
        UUID eventId = UUID.randomUUID();
        assertThat(auditLogRepository.existsByEventId(eventId)).isFalse();

        auditLogRepository.saveAndFlush(entry(eventId, UUID.randomUUID()));

        assertThat(auditLogRepository.existsByEventId(eventId)).isTrue();
    }

    @Test
    void secondEntryWithSameEventId_isRejectedByUniqueConstraint() {
        UUID eventId = UUID.randomUUID();
        auditLogRepository.saveAndFlush(entry(eventId, UUID.randomUUID()));

        assertThatThrownBy(() -> auditLogRepository.saveAndFlush(entry(eventId, UUID.randomUUID())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_isRejectedByTrigger() {
        AuditLog saved = auditLogRepository.saveAndFlush(entry(UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_logs SET event_type = 'tampered' WHERE id = ?", saved.getId()))
                .hasMessageContaining("append-only");
    }

    @Test
    void delete_isRejectedByTrigger() {
        AuditLog saved = auditLogRepository.saveAndFlush(entry(UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit_logs WHERE id = ?", saved.getId()))
                .hasMessageContaining("append-only");
    }
}
