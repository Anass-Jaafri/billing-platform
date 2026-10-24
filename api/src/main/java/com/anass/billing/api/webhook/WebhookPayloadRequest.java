package com.anass.billing.api.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record WebhookPayloadRequest(
        @NotBlank
        String eventId,
        @NotNull
        UUID chargeId,
        @NotBlank
        @Pattern(regexp = "SUCCEEDED|FAILED", message = "outcome must be SUCCEEDED or FAILED")
        String outcome
) {
}
