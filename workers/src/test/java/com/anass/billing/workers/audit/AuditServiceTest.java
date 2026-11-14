package com.anass.billing.workers.audit;

import com.anass.billing.common.audit.AuditLog;
import com.anass.billing.common.audit.AuditLogRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    @Mock
    AuditLogRepository auditLogRepository;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository, objectMapper);
    }

    private ChargeEvent event(String type) {
        return new ChargeEvent(type, UUID.randomUUID(), Instant.now(),
                new ChargeEvent.ChargeEventData(UUID.randomUUID(), 5000, "MAD", "cust_123"));
    }

    @Test
    void record_whenNew_savesEntryWithFullEventAsPayload() throws Exception {
        ChargeEvent event = event("charge.confirmed");
        when(auditLogRepository.existsByEventId(event.eventId())).thenReturn(false);

        boolean written = auditService.record(event);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(saved.capture());
        AuditLog entry = saved.getValue();
        assertThat(written).isTrue();
        assertThat(entry.getEventId()).isEqualTo(event.eventId());
        assertThat(entry.getChargeId()).isEqualTo(event.data().chargeId());
        assertThat(entry.getEventType()).isEqualTo("charge.confirmed");

        JsonNode payload = objectMapper.readTree(entry.getPayload());
        assertThat(payload.get("eventId").asText()).isEqualTo(event.eventId().toString());
        assertThat(payload.get("data").get("customerRef").asText()).isEqualTo("cust_123");
        assertThat(payload.get("data").get("amount").asLong()).isEqualTo(5000);
    }

    @Test
    void record_whenFailedEvent_isAuditedToo() {
        ChargeEvent event = event("charge.failed");
        when(auditLogRepository.existsByEventId(event.eventId())).thenReturn(false);

        auditService.record(event);

        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo("charge.failed");
    }

    @Test
    void record_whenAlreadyRecorded_skipsWithoutSaving() {
        ChargeEvent event = event("charge.confirmed");
        when(auditLogRepository.existsByEventId(event.eventId())).thenReturn(true);

        boolean written = auditService.record(event);

        assertThat(written).isFalse();
        verify(auditLogRepository, never()).saveAndFlush(any());
    }
}