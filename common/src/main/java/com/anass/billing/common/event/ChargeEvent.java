package com.anass.billing.common.event;

import com.anass.billing.common.charge.Charge;

import java.time.Instant;
import java.util.UUID;

public record ChargeEvent(
        String eventType,
        UUID eventId,
        Instant occurredAt,
        ChargeEventData data

) {
    public static ChargeEvent confirmed(Charge charge) {
        return new ChargeEvent(
                "charge.confirmed",
                UUID.randomUUID(),
                Instant.now(),
                new ChargeEventData(charge.getId(), charge.getAmount(), charge.getCurrency(), charge.getCustomerRef())
        );
    }

    public static ChargeEvent failed(Charge charge) {
        return new ChargeEvent(
                "charge.failed",
                UUID.randomUUID(),
                Instant.now(),
                new ChargeEventData(charge.getId(), charge.getAmount(), charge.getCurrency(), charge.getCustomerRef())
        );
    }

    public record ChargeEventData(UUID chargeId, long amount, String currency, String customerRef) {
    }
}

