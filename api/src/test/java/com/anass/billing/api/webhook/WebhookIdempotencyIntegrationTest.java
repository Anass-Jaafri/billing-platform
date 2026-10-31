package com.anass.billing.api.webhook;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.status.ChargeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Testcontainers

public class WebhookIdempotencyIntegrationTest {


    private static final String TEST_QUEUE = "wh-7-test-queue";
    // static = started once for the whole class, shared by every test
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    // Containers get random ports, so tell Spring where they ended up
    @DynamicPropertySource
    static void connectAppToContainers(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }
    @Autowired private TestRestTemplate http;
    @Autowired private ChargeRepository chargeRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private FanoutExchange chargeEventsExchange;
    @Autowired private WebhookService webhookService;
    @Autowired private TransactionTemplate transactionTemplate;
    @SpyBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void bindEmptyTestQueue(){
        Queue queue = new Queue(TEST_QUEUE, false,false,true);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(chargeEventsExchange));
        amqpAdmin.purgeQueue(TEST_QUEUE);
    }
    // ---------- helpers ----------

    private Charge newCharge() {
        return chargeRepository.save(
                Charge.builder().customerRef("cust_it").amount(5000).currency("USD").build());
    }

    private String newEventId() {
        return "evt_" + UUID.randomUUID();
    }

    private ResponseEntity<String> sendWebhook(String eventId, UUID chargeId, String outcome) {
        return http.postForEntity(
                "/webhooks/payment-provider",
                Map.of("eventId", eventId, "chargeId", chargeId.toString(), "outcome", outcome),
                String.class);
    }

    private int webhookRows(String eventId) {
        return jdbc.queryForObject(
                "select count(*) from webhook_events where provider_event_id = ?", Integer.class, eventId);
    }

    private Charge reload(Charge charge) {
        return chargeRepository.findById(charge.getId()).orElseThrow();
    }

    private int messagesInTestQueue() {
        return amqpAdmin.getQueueInfo(TEST_QUEUE).getMessageCount();
    }

    private void assertExactlyOneMessageArrives() {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(messagesInTestQueue()).isEqualTo(1));
    }

    private void assertNoMessageEverArrives() {
        // must STAY empty for a second, not just be empty at one instant
        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(messagesInTestQueue()).isZero());
    }

    private boolean stickyNoteExists(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("webhook:" + eventId));
    }

    // ---------- tests ----------

    @Test
    void singleWebhook_confirmsCharge_recordsItAsProcessed_andDeliversOneMessage() {
        Charge charge = newCharge();
        String eventId = newEventId();

        ResponseEntity<String> response = sendWebhook(eventId, charge.getId(), "SUCCEEDED");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(reload(charge).getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        assertThat(webhookRows(eventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select processed_at from webhook_events where provider_event_id = ?",
                Timestamp.class,
                eventId))
                .isNotNull();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(stickyNoteExists(eventId)).isTrue());

        assertExactlyOneMessageArrives();
        Message message = rabbitTemplate.receive(TEST_QUEUE, 2000);
        assertThat(message).isNotNull();
        assertThat(new String(message.getBody())).contains("charge.confirmed");
    }

    @Test
    void sameEventTwice_caughtByStickyNote_changesChargeOnce() {
        Charge charge = newCharge();
        String eventId = newEventId();

        sendWebhook(eventId, charge.getId(), "SUCCEEDED");
        LocalDateTime changedAt = reload(charge).getLastModifiedDate();
        assertThat(changedAt).isNotNull();

        ResponseEntity<String> second = sendWebhook(eventId, charge.getId(), "SUCCEEDED");

        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(webhookRows(eventId)).isEqualTo(1);
        assertThat(reload(charge).getLastModifiedDate()).isEqualTo(changedAt); // not touched again
        assertExactlyOneMessageArrives();
    }

    @Test
    void sameEventTwice_whenStickyNoteIsLost_theDatabaseRuleStillCatchesIt() {
        Charge charge = newCharge();
        String eventId = newEventId();

        sendWebhook(eventId, charge.getId(), "SUCCEEDED");
        LocalDateTime changedAt = reload(charge).getLastModifiedDate();

        // Simulate the note expiring or Redis restarting
        redisTemplate.delete("webhook:" + eventId);

        ResponseEntity<String> second = sendWebhook(eventId, charge.getId(), "SUCCEEDED");

        assertThat(second.getStatusCode().value()).isEqualTo(200); // a 500 would mean confirm() ran twice
        assertThat(webhookRows(eventId)).isEqualTo(1);
        assertThat(reload(charge).getLastModifiedDate()).isEqualTo(changedAt);
        assertExactlyOneMessageArrives();
    }

    @Test
    void duplicatesSentAtTheSameInstant_stillLeaveOneRowAndOneMessage() throws Exception {
        Charge charge = newCharge();
        String eventId = newEventId();
        int copies = 5;

        ExecutorService pool = Executors.newFixedThreadPool(copies);
        CountDownLatch onTheLine = new CountDownLatch(copies);
        CountDownLatch startingGun = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < copies; i++) {
                futures.add(pool.submit(() -> {
                    onTheLine.countDown();
                    startingGun.await();
                    return sendWebhook(eventId, charge.getId(), "SUCCEEDED").getStatusCode().value();
                }));
            }
            onTheLine.await();
            startingGun.countDown();
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(webhookRows(eventId)).isEqualTo(1);
        assertThat(reload(charge).getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        assertExactlyOneMessageArrives();
    }

    @Test
    void unknownCharge_returns404_andStoresNothing() {
        String eventId = newEventId();

        ResponseEntity<String> response = sendWebhook(eventId, UUID.randomUUID(), "SUCCEEDED");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(webhookRows(eventId)).isZero();
        assertNoMessageEverArrives();
    }

    @Test
    void rabbitFailure_rollsEverythingBack_andTheRetrySucceeds() {
        Charge charge = newCharge();
        String eventId = newEventId();

        doThrow(new AmqpException("rabbit is down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        ResponseEntity<String> failed = sendWebhook(eventId, charge.getId(), "SUCCEEDED");

        assertThat(failed.getStatusCode().value()).isEqualTo(500);
        assertThat(webhookRows(eventId)).isZero();                      // record rolled back
        assertThat(reload(charge).getStatus()).isEqualTo(ChargeStatus.PENDING); // charge rolled back
        assertThat(stickyNoteExists(eventId)).isFalse();                // no false "done" note
        assertNoMessageEverArrives();

        reset(rabbitTemplate); // "RabbitMQ is back"

        ResponseEntity<String> retry = sendWebhook(eventId, charge.getId(), "SUCCEEDED");

        assertThat(retry.getStatusCode().value()).isEqualTo(200);
        assertThat(webhookRows(eventId)).isEqualTo(1);
        assertThat(reload(charge).getStatus()).isEqualTo(ChargeStatus.CONFIRMED);
        assertExactlyOneMessageArrives();
    }

    @Test
    void rolledBackTransaction_neverDeliversItsMessage() {
        Charge charge = newCharge();
        String eventId = newEventId();

        transactionTemplate.executeWithoutResult(status -> {
            webhookService.receiveWebhook(new WebhookPayloadRequest(eventId, charge.getId(), "SUCCEEDED"));
            status.setRollbackOnly(); // pretend the database commit never happened
        });

        assertThat(reload(charge).getStatus()).isEqualTo(ChargeStatus.PENDING);
        assertThat(webhookRows(eventId)).isZero();
        assertThat(stickyNoteExists(eventId)).isFalse();
        assertNoMessageEverArrives(); // the ADR-005 promise: rollback discards the publishing
    }
}
