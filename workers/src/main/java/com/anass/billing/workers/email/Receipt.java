package com.anass.billing.workers.email;

public record Receipt(String recipient, String subject, String body) {
}
