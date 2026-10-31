package com.anass.billing.common.event;

import com.anass.billing.common.charge.Charge;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChargeEvent(
        String eventType,
        UUID eventId,
        LocalDateTime occurredAt,
        ChargeEventData data

) {
    public record  ChargeEventData(UUID chargeId,long amount, String currency, String customerRef){}

        public static ChargeEvent confirmed(Charge charge){
            return new ChargeEvent(
                    "charge.confirmed",
                    UUID.randomUUID(),
                    LocalDateTime.now(),
                    new ChargeEventData(charge.getId(), charge.getAmount(), charge.getCurrency(), charge.getCustomerRef())
            );
        }
        public static ChargeEvent failed(Charge charge) {
            return new ChargeEvent(
                    "charge.failed",
                    UUID.randomUUID(),
                    LocalDateTime.now(),
                    new ChargeEventData(charge.getId(), charge.getAmount(), charge.getCurrency(), charge.getCustomerRef())
            );
        }
    }

