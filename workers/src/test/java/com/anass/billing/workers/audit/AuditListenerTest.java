package com.anass.billing.workers.audit;

import com.anass.billing.common.event.ChargeEvent;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AuditListenerTest {

    @Mock
    AuditService auditService;
    @InjectMocks
    AuditListener listener;

    @ParameterizedTest
    @ValueSource(strings = {"charge.confirmed", "charge.failed"})
    void onChargeEvent_recordsEveryEventType(String type) {
        ChargeEvent event = new ChargeEvent(type, UUID.randomUUID(), Instant.now(),
                new ChargeEvent.ChargeEventData(UUID.randomUUID(), 5000, "MAD", "cust_123"));
        listener.onChargeEvent(event);
        verify(auditService).record(event);
    }
}
