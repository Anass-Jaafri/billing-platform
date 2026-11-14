package com.anass.billing.workers.email;

import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.messaging.Messaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component

public class EmailListener {

    private static final Logger log = LoggerFactory.getLogger(EmailListener.class);
    private static final String CHARGE_CONFIRMED = "charge.confirmed";

    private final ReceiptService receiptService;

    public EmailListener(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @RabbitListener(queues = Messaging.EMAIL_QUEUE)
    public void onChargeEvent(ChargeEvent event) {
        if (!CHARGE_CONFIRMED.equals(event.eventType())) {
            log.debug("Ignoring {} event", event.eventType());
            return;
        }

        receiptService.sendReceiptFor(event);
    }
}

