package com.anass.billing.api.exception;

public class UnknownWorkerException extends RuntimeException {
    public UnknownWorkerException(String worker) {
        super("Unknown worker: \" + worker + \" (expected invoice, email or audit)");
    }
}
