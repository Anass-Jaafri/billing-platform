package com.anass.billing.api.charge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateChargeRequest(
        @NotBlank String customerRef,
        @Valid
        @Positive long amount,
        @NotBlank @Pattern(regexp = "USD", message = "only USD is supported")
        String currency
) {
}
