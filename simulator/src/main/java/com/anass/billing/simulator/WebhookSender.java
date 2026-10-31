package com.anass.billing.simulator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class WebhookSender {
    public record SendResult(int status, String body){}

    private final HttpClient httpClient;
    private final URI webhookUri;

    public WebhookSender(HttpClient httpClient, String baseUrl){
        this.httpClient = httpClient;
        this.webhookUri = URI.create(baseUrl + "/webhooks/payment-provider");
    }

    public SendResult send(String eventId, UUID chargeId, String outcome)
        throws IOException, InterruptedException{
        String json = String.format("{\"eventId\":\"%s\",\"chargeId\":\"%s\",\"outcome\":\"%s\"}",
                eventId, chargeId, outcome);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(webhookUri)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new SendResult(response.statusCode(), response.body());
    }
}
