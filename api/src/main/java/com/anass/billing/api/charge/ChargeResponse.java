package com.anass.billing.api.charge;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.status.ChargeStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChargeResponse(
        UUID id,
        String customerRef,
        long amount,
        String currency,
        ChargeStatus status,
        LocalDateTime createdDate
) {
    public static ChargeResponse from(Charge charge) {
        return new ChargeResponse(
                charge.getId(),
                charge.getCustomerRef(),
                charge.getAmount(),
                charge.getCurrency(),
                charge.getStatus(),
                charge.getCreatedDate()
        );
    }
}
