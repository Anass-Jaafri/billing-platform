package com.anass.billing.workers.invoice;

import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.messaging.Messaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class InvoiceListener {

    private static final Logger log = LoggerFactory.getLogger(InvoiceListener.class);
    private static final String CHARGE_CONFIRMED = "charge.confirmed";

    private final InvoiceService invoiceService;

    public InvoiceListener(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @RabbitListener(queues = Messaging.INVOICE_QUEUE)
    public void onChargeEvent(ChargeEvent event) {
        if (!CHARGE_CONFIRMED.equals(event.eventType())) {
            log.debug("Ignoring {} event", event.eventType());
            return;
        }
        invoiceService.generateFor(event.data().chargeId());
    }
}
