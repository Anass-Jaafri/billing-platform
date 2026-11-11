package com.anass.billing.workers.invoice;

import com.anass.billing.common.event.ChargeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InvoiceListenerTest {

    @Mock
    InvoiceService invoiceService;
    @InjectMocks
    InvoiceListener listener;

    private ChargeEvent event(String type, UUID chargeId) {
        return new ChargeEvent(type, UUID.randomUUID(), Instant.now(),
                new ChargeEvent.ChargeEventData(chargeId, 5000, "MAD", "cust_123"));
    }

    @Test
    void onChargeEvent_forConfirmedCharge_generatesInvoice() {
        UUID chargeId = UUID.randomUUID();

        listener.onChargeEvent(event("charge.confirmed", chargeId));

        verify(invoiceService).generateFor(chargeId);
    }

    @Test
    void onChargeEvent_forFailedCharge_isIgnored() {
        listener.onChargeEvent(event("charge.failed", UUID.randomUUID()));

        verifyNoInteractions(invoiceService);
    }
}