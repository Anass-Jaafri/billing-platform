package com.anass.billing.api.exception;

import com.anass.billing.common.status.ChargeStatus;

import java.util.UUID;

public class ChargeAlreadySettledException extends RuntimeException {
    public ChargeAlreadySettledException(UUID chargeId, ChargeStatus current, String reportedOutcome) {
        super("Charge " + chargeId + " is already " + current
                + "; the provider reported " + reportedOutcome);
    }
}