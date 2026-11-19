package com.anass.billing.common.messaging;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum DlqWorker {
    INVOICE("invoice", Messaging.INVOICE_QUEUE, Messaging.INVOICE_DLQ),
    EMAIL("email", Messaging.EMAIL_QUEUE, Messaging.EMAIL_DLQ),
    AUDIT("audit", Messaging.AUDIT_QUEUE, Messaging.AUDIT_DLQ);

    private final String pathName;
    private final String workQueue;
    private final String deadLetterQueue;

    DlqWorker(String pathName, String workQueue, String deadLetterQueue) {
        this.pathName = pathName;
        this.workQueue = workQueue;
        this.deadLetterQueue = deadLetterQueue;
    }

    public static Optional<DlqWorker> fromPathName(String pathName) {
        return Arrays.stream(values()).filter(w -> w.pathName.equals(pathName)).findFirst();
    }
}