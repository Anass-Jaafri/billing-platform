package com.anass.billing.workers.email;

import com.anass.billing.common.event.ChargeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class EmailListenerTest {
    @Mock
    ReceiptService receiptService;
    @InjectMocks
    EmailListener listener;

    private ChargeEvent event(String type) {
        return new ChargeEvent(type, UUID.randomUUID(), LocalDateTime.now(),
                new ChargeEvent.ChargeEventData(UUID.randomUUID(), 5000, "MAD", "cust_123"));
    }

    @Test
    void onChargeEvent_forConfirmedCharge_sendsReceipt() {
        ChargeEvent confirmed = event("charge.confirmed");
        listener.onChargeEvent(confirmed);
        verify(receiptService).sendReceiptFor(confirmed);
    }

    @Test
    void onChargeEvent_forFailedCharge_isIgnored() {
        listener.onChargeEvent(event("charge.failed"));
        verifyNoInteractions(receiptService);
    }
}
