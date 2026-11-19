package com.anass.billing.common.messaging;

public class DlqMessageNotFoundException extends RuntimeException {
    public DlqMessageNotFoundException(DlqWorker worker, String messageId) {
        super("No message " + messageId + " in the " + worker.getPathName() + " dead-letter queue");
    }
}