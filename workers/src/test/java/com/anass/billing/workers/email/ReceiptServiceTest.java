package com.anass.billing.workers.email;

import com.anass.billing.common.event.ChargeEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ReceiptServiceTest {
    @Mock
    ReceiptSender receiptSender;
    @InjectMocks
    ReceiptService receiptService;

    @Test
    void sendReceiptFor_buildsReceiptFromEvent() {
        UUID chargeId = UUID.randomUUID();
        ChargeEvent event = new ChargeEvent("charge.confirmed", UUID.randomUUID(), Instant.now(),
                new ChargeEvent.ChargeEventData(chargeId, 5000, "MAD", "cust_123"));

        receiptService.sendReceiptFor(event);

        ArgumentCaptor<Receipt> sent = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptSender).send(sent.capture());
        assertThat(sent.getValue().recipient()).isEqualTo("cust_123");
        assertThat(sent.getValue().subject()).isEqualTo("Payment receipt");
        assertThat(sent.getValue().body()).contains("50.00 MAD").contains(chargeId.toString());
    }
}
