package com.anass.billing.common.messaging;


/** Names shared by api (publisher) and workers (consumers) so they can never drift apart. */
public class Messaging {

    public static final String CHARGE_EVENTS_EXCHANGE = "charge-events";

    public static final String INVOICE_QUEUE = "invoice-queue";
    public static final String EMAIL_QUEUE = "email-queue";
    public static final String AUDIT_QUEUE = "audit-queue";

    private Messaging(){}
}
