package com.anass.billing.api.exception;

public class DuplicateWebhookException extends RuntimeException{
    public DuplicateWebhookException(String eventId){
        super("Webhook already received: " + eventId);
    }
}
