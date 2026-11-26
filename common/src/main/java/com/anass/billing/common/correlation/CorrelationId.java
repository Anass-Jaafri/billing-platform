package com.anass.billing.common.correlation;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private CorrelationId() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Only values that cannot forge log lines are accepted from outside.
     */
    public static boolean isSafe(String value) {
        return value != null && SAFE.matcher(value).matches();
    }
}