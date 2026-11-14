package com.anass.billing.workers.email;

public interface ReceiptSender {
    void send(Receipt receipt);
}
