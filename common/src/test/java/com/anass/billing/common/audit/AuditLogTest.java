package com.anass.billing.common.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuditLogTest {

    @Test
    void forEvent_setsFieldsAndRecordedAt() {
        UUID eventId = UUID.randomUUID();
        UUID chargeId = UUID.randomUUID();

        AuditLog log = AuditLog.forEvent(eventId, chargeId, "charge.confirmed", "{\"a\":1}");

        assertThat(log.getEventId()).isEqualTo(eventId);
        assertThat(log.getChargeId()).isEqualTo(chargeId);
        assertThat(log.getEventType()).isEqualTo("charge.confirmed");
        assertThat(log.getPayload()).isEqualTo("{\"a\":1}");
        assertThat(log.getRecordedAt()).isNotNull();
    }

    @Test
    void forEvent_withMissingValues_throws() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> AuditLog.forEvent(null, id, "charge.confirmed", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLog.forEvent(id, null, "charge.confirmed", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLog.forEvent(id, id, " ", "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AuditLog.forEvent(id, id, "charge.confirmed", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
