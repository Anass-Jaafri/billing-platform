package com.anass.billing.workers.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingReceiptSender implements ReceiptSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingReceiptSender.class);

    @Override
    public void send(Receipt receipt) {
        log.info("[MOCK EMAIL] to={} subject=\"{}\" body=\"{}\"", receipt.recipient(), receipt.subject(), receipt.body());
    }
}
