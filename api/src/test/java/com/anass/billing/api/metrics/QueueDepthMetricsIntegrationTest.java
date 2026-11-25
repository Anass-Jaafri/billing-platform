package com.anass.billing.api.metrics;

import com.anass.billing.common.messaging.Messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;

import static com.anass.billing.api.metrics.QueueDepthMetrics.METRIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class QueueDepthMetricsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    MeterRegistry registry;
    @Autowired
    AmqpAdmin amqpAdmin;
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

    @Test
    void gauge_followsTheRealNumberOfWaitingMessages() {
        // workers declare the real queues; they are not running in this test, so declare one here
        amqpAdmin.declareQueue(new Queue(Messaging.INVOICE_QUEUE, false));
        amqpAdmin.purgeQueue(Messaging.INVOICE_QUEUE);
        for (int i = 0; i < 3; i++) {
            rabbitTemplate.convertAndSend("", Messaging.INVOICE_QUEUE, "message-" + i);
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(depth(Messaging.INVOICE_QUEUE)).isEqualTo(3));

        amqpAdmin.purgeQueue(Messaging.INVOICE_QUEUE);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(depth(Messaging.INVOICE_QUEUE)).isZero());
    }

    @Test
    void aQueueThatDoesNotExist_isNaN() {
        assertThat(depth(Messaging.AUDIT_DLQ)).isNaN();
    }

    @Test
    void metricEndpoint_isOpenToAdmin() throws Exception {
        mockMvc.perform(get("/actuator/metrics/" + METRIC).header("Authorization", "Bearer " + token("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(METRIC));
    }

    @Test
    void metricEndpoint_isForbiddenToMerchant() throws Exception {
        mockMvc.perform(get("/actuator/metrics/" + METRIC).header("Authorization", "Bearer " + token("merchant", "merchant123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void metricEndpoint_requiresAToken() throws Exception {
        mockMvc.perform(get("/actuator/metrics/" + METRIC)).andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private double depth(String queue) {
        return registry.get(METRIC).tag("queue", queue).gauge().value();
    }

    private String token(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }
}