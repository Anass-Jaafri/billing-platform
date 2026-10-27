package com.anass.billing.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.anass.billing.simulator.WebhookSender.SendResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderSimulatorTest {

    private static final String CHARGE = "eb3560cf-a886-464d-baac-0f26662f5669";

    private HttpServer fakeApi;
    private String baseUrl;
    // Thread-safe list: simultaneous requests may arrive on different threads
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startFakeApi() throws IOException {
        fakeApi = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeApi.createContext("/webhooks/payment-provider", exchange -> {
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1); // 200, empty body
            exchange.close();
        });
        fakeApi.start();
        baseUrl = "http://localhost:" + fakeApi.getAddress().getPort();
    }

    @AfterEach
    void stopFakeApi() {
        fakeApi.stop(0);
    }

    private ProviderSimulator simulator() {
        return new ProviderSimulator(new WebhookSender(HttpClient.newHttpClient(), baseUrl));
    }

    private SimulatorConfig config(String... extraArgs) {
        List<String> args = new ArrayList<>(
                List.of("--charge", CHARGE, "--url", baseUrl, "--event-id", "evt_test"));
        args.addAll(List.of(extraArgs));
        return SimulatorConfig.parse(args.toArray(new String[0]));
    }

    @Test
    void sendsOneWebhookWithTheGivenFields() throws Exception {
        List<SendResult> results = simulator().run(config("--outcome", "FAILED"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(200);
        assertThat(receivedBodies).containsExactly(
                "{\"eventId\":\"evt_test\",\"chargeId\":\"" + CHARGE + "\",\"outcome\":\"FAILED\"}");
    }

    @Test
    void duplicatesReuseTheSameEventId() throws Exception {
        simulator().run(config("--duplicates", "2"));

        assertThat(receivedBodies).hasSize(3);
        assertThat(receivedBodies)
                .allSatisfy(body -> assertThat(body).contains("\"eventId\":\"evt_test\""));
    }

    @Test
    void simultaneousDuplicatesAllArrive() throws Exception {
        List<SendResult> results = simulator().run(config("--duplicates", "3", "--simultaneous"));

        assertThat(results).hasSize(4);
        assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo(200));
        assertThat(receivedBodies).hasSize(4);
    }
}