package com.anass.billing.common.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "charge_id", nullable = false, updatable = false)
    private UUID chargeId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false, updatable = false)
    private String payload;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    public static AuditLog forEvent(UUID eventId, UUID chargeId, String eventType, String payload) {
        if (chargeId == null || eventId == null) {
            throw new IllegalArgumentException("eventId and chargeId are required");
        }
        if (eventType == null || eventType.isBlank() || payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("eventType and payload are required");
        }

        AuditLog log = new AuditLog();
        log.eventId = eventId;
        log.chargeId = chargeId;
        log.eventType = eventType;
        log.payload = payload;
        log.recordedAt = Instant.now();
        return log;
    }
}
