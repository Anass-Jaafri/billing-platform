package com.anass.billing.api.dlq;

import com.fasterxml.jackson.databind.JsonNode;

public record DlqMessageResponse(String messageId, long failureCount, JsonNode payload) {
}