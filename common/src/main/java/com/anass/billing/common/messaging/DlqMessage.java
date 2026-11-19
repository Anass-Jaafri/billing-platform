package com.anass.billing.common.messaging;

public record DlqMessage(String messageId, long failureCount, String payload) {
}