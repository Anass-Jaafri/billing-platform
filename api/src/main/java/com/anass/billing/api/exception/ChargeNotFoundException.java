package com.anass.billing.api.exception;

import java.util.UUID;

public class ChargeNotFoundException extends RuntimeException {
    public ChargeNotFoundException(UUID chargeId) {
        super("Could not find charge with id: " + chargeId);
    }
}
