package com.anass.billing.common.messaging;


/**
 * Names shared by api (publisher) and workers (consumers) so they can never drift apart.
 */
public class Messaging {

    public static final String CHARGE_EVENTS_EXCHANGE = "charge-events";

    public static final String INVOICE_QUEUE = "invoice-queue";
    public static final String EMAIL_QUEUE = "email-queue";
    public static final String AUDIT_QUEUE = "audit-queue";

    public static final String DEAD_LETTER_EXCHANGE = "charge-events.dlx";
    public static final String INVOICE_DLQ = INVOICE_QUEUE + ".dlq";
    public static final String EMAIL_DLQ = EMAIL_QUEUE + ".dlq";
    public static final String AUDIT_DLQ = AUDIT_QUEUE + ".dlq";

    private Messaging() {
    }
}
