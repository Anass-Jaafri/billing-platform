package com.anass.billing.api.correlation;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.correlation.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CorrelationIdPropagationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static final String TEST_QUEUE = "obs-4-test-queue";

    @Autowired
    TestRestTemplate http;
    @Autowired
    ChargeRepository chargeRepository;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    FanoutExchange chargeEventsExchange;
    @Autowired
    RabbitTemplate rabbitTemplate;

    @DynamicPropertySource
    static void connectAppToContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @BeforeEach
    void bindEmptyTestQueue() {
        Queue queue = new Queue(TEST_QUEUE, false, false, true);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(chargeEventsExchange));
        amqpAdmin.purgeQueue(TEST_QUEUE);
    }

    @Test
    void webhookWithCorrelationHeader_publishesTheSameIdOnTheMessage() {
        ResponseEntity<String> response = sendWebhook("req-from-client-1");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("req-from-client-1");
        assertThat(publishedMessage().getMessageProperties().getCorrelationId()).isEqualTo("req-from-client-1");
    }

    @Test
    void webhookWithoutHeader_publishesTheGeneratedIdThatTheResponseReturned() {
        ResponseEntity<String> response = sendWebhook(null);

        String returned = response.getHeaders().getFirst(CorrelationId.HEADER);
        assertThat(CorrelationId.isSafe(returned)).isTrue();
        assertThat(publishedMessage().getMessageProperties().getCorrelationId()).isEqualTo(returned);
    }

    // --- helpers ---

    private ResponseEntity<String> sendWebhook(String correlationId) {
        Charge charge = chargeRepository.save(
                Charge.builder().customerRef("cust_corr").amount(5000).currency("USD").build());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (correlationId != null) {
            headers.set(CorrelationId.HEADER, correlationId);
        }
        Map<String, String> body = Map.of(
                "eventId", "evt_" + UUID.randomUUID(),
                "chargeId", charge.getId().toString(),
                "outcome", "SUCCEEDED");
        return http.exchange("/webhooks/payment-provider", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private Message publishedMessage() {
        Message message = rabbitTemplate.receive(TEST_QUEUE, 5000);
        assertThat(message).as("a ChargeEvent should have been published").isNotNull();
        return message;
    }
}