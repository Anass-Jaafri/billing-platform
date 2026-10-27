package com.anass.billing.simulator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record SimulatorConfig(
        String baseUrl,
        UUID chargeId,
        String outcome,
        int delaySeconds,
        int duplicates,
        boolean simultaneous,
        String eventId
) {

    public static SimulatorConfig parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        boolean simultaneous = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + arg);
            }
            String name = arg.substring(2);
            if (name.equals("simultaneous")) {
                simultaneous = true; // a switch: no value follows it
            } else {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for --" + name);
                }
                options.put(name, args[++i]);
            }
        }

        String charge = options.remove("charge");
        if (charge == null) {
            throw new IllegalArgumentException("--charge <uuid> is required");
        }
        UUID chargeId;
        try {
            chargeId = UUID.fromString(charge);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--charge must be a valid UUID: " + charge);
        }

        String baseUrl = take(options, "url", "http://localhost:8080").replaceAll("/+$", "");
        String outcome = take(options, "outcome", "SUCCEEDED");
        int delay = Integer.parseInt(take(options, "delay", "0"));
        int duplicates = Integer.parseInt(take(options, "duplicates", "0"));
        String eventId = take(options, "event-id", "evt_" + UUID.randomUUID().toString().substring(0, 8));

        if (delay < 0 || duplicates < 0) {
            throw new IllegalArgumentException("--delay and --duplicates must be 0 or more");
        }
        if (!options.isEmpty()) {
            throw new IllegalArgumentException("Unknown option(s): " + options.keySet());
        }

        return new SimulatorConfig(baseUrl, chargeId, outcome, delay, duplicates, simultaneous, eventId);
    }

    private static String take(Map<String, String> options, String name, String defaultValue) {
        String value = options.remove(name);
        return value != null ? value : defaultValue;
    }
}