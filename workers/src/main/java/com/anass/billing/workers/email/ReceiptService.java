package com.anass.billing.workers.email;

import com.anass.billing.common.event.ChargeEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ReceiptService {

    private final ReceiptSender receiptSender;

    public ReceiptService(ReceiptSender receiptSender) {
        this.receiptSender = receiptSender;
    }

    public void sendReceiptFor(ChargeEvent event) {
        ChargeEvent.ChargeEventData data = event.data();
        String amount = BigDecimal.valueOf(data.amount(), 2).toPlainString() + " " + data.currency();

        receiptSender.send(new Receipt(
                data.customerRef(),
                "Payment receipt",
                "Your payment of " + amount + "was confirmed. Reference: " + data.chargeId()
        ));
    }
}
