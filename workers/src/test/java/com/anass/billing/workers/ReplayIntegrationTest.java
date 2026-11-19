package com.anass.billing.workers;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.messaging.DlqOperations;
import com.anass.billing.common.messaging.DlqWorker;
import com.anass.billing.common.messaging.Messaging;
import com.anass.billing.workers.email.ReceiptSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class ReplayIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String MESSAGE_ID = "replay-test-1";
    @MockBean
    ReceiptSender receiptSender;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    ConnectionFactory connectionFactory;
    @Autowired
    ChargeRepository chargeRepository;
    private DlqOperations dlqOperations;

    @DynamicPropertySource
    static void connectToContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @BeforeEach
    void setUp() {
        dlqOperations = new DlqOperations(connectionFactory);
        amqpAdmin.purgeQueue(Messaging.EMAIL_QUEUE);
        amqpAdmin.purgeQueue(Messaging.EMAIL_DLQ);
        reset(receiptSender);
    }

    @Test
    void failedMessage_isReplayedAfterTheFix_andSucceeds() {
        deadLetterAnEmailEvent();

        // "fix" the problem: the sender works again
        reset(receiptSender);

        dlqOperations.replay(DlqWorker.EMAIL, MESSAGE_ID);

        // processed exactly once (not looping), DLQ and work queue empty
        verify(receiptSender, after(2_000).times(1)).send(any());
        assertThat(amqpAdmin.getQueueInfo(Messaging.EMAIL_DLQ).getMessageCount()).isZero();
        assertThat(amqpAdmin.getQueueInfo(Messaging.EMAIL_QUEUE).getMessageCount()).isZero();
    }

    @Test
    void replayWhileStillBroken_failsAgain_andReturnsToTheDlqWithAHigherCount() {
        deadLetterAnEmailEvent();

        // NOT fixed: replay anyway
        dlqOperations.replay(DlqWorker.EMAIL, MESSAGE_ID);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(dlqOperations.list(DlqWorker.EMAIL, 50))
                        .singleElement()
                        .satisfies(m -> assertThat(m.failureCount()).isEqualTo(2)));
        verify(receiptSender, times(6)).send(any());   // 3 attempts, replay, 3 more
    }

    /**
     * Publishes a charge event whose email processing fails every attempt, and waits until it is in the DLQ.
     */
    private void deadLetterAnEmailEvent() {
        doThrow(new RuntimeException("SMTP is down")).when(receiptSender).send(any());
        Charge charge = chargeRepository.saveAndFlush(Charge.builder()
                .customerRef("cust_replay_" + UUID.randomUUID()).amount(5000).currency("MAD").build());

        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", ChargeEvent.confirmed(charge),
                message -> {
                    message.getMessageProperties().setMessageId(MESSAGE_ID);
                    return message;
                });

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(amqpAdmin.getQueueInfo(Messaging.EMAIL_DLQ).getMessageCount()).isEqualTo(1));
    }
}