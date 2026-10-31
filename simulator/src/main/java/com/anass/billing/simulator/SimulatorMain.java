package com.anass.billing.simulator;

import java.net.http.HttpClient;
import java.util.List;

public class SimulatorMain {

    private static final String USAGE = """
            Usage: simulator --charge <uuid> [options]

              --charge <uuid>      charge the webhook is about (required)
              --outcome <value>    SUCCEEDED (default) or FAILED
              --delay <seconds>    wait before sending (default 0)
              --duplicates <n>     extra copies of the same event (default 0)
              --simultaneous       send all copies at the same instant
              --event-id <id>      event id to use (default: random)
              --url <base-url>     API address (default http://localhost:8080)
            """;

    public static void main(String[] args) throws Exception {
        SimulatorConfig config;
        try {
            config = SimulatorConfig.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            System.err.println(USAGE);
            System.exit(1);
            return;
        }

        System.out.printf("Event %s for charge %s (outcome %s)%n",
                config.eventId(), config.chargeId(), config.outcome());
        if (config.delaySeconds() > 0) {
            System.out.printf("Waiting %d seconds first...%n", config.delaySeconds());
        }

        ProviderSimulator simulator =
                new ProviderSimulator(new WebhookSender(HttpClient.newHttpClient(), config.baseUrl()));
        List<WebhookSender.SendResult> results = simulator.run(config);

        for (int i = 0; i < results.size(); i++) {
            WebhookSender.SendResult result = results.get(i);
            String body = result.body().isBlank() ? "" : "  " + result.body();
            System.out.printf("  copy %d -> HTTP %d%s%n", i + 1, result.status(), body);
        }
    }
}