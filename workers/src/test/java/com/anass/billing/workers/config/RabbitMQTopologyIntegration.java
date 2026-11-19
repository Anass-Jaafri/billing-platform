package com.anass.billing.workers.config;


import com.anass.billing.common.messaging.Messaging;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
public class RabbitMQTopologyIntegration {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    // The workers app still needs a database to start, even though this test never uses it
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;

    @DynamicPropertySource
    static void connectToContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @BeforeEach
    void purgeAllQueues() {
        for (String queue : List.of(Messaging.INVOICE_QUEUE, Messaging.EMAIL_QUEUE, Messaging.AUDIT_QUEUE,
                Messaging.INVOICE_DLQ, Messaging.EMAIL_DLQ, Messaging.AUDIT_DLQ)) {
            amqpAdmin.purgeQueue(queue);
        }
    }

    @Test
    void oneEvent_isCopiedIntoEveryWorkerQueue() {
        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", "one event");

        for (String queue : List.of(Messaging.INVOICE_QUEUE, Messaging.EMAIL_QUEUE, Messaging.AUDIT_QUEUE)) {
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() ->
                            assertThat(amqpAdmin.getQueueInfo(queue).getMessageCount()).isEqualTo(1));
        }
    }

    @Test
    void deadLetterExchange_routesEachMessageToItsOwnWorkersDlq() {
        rabbitTemplate.convertAndSend(Messaging.DEAD_LETTER_EXCHANGE, Messaging.EMAIL_QUEUE, "dead email");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(amqpAdmin.getQueueInfo(Messaging.EMAIL_DLQ).getMessageCount()).isEqualTo(1));
        assertThat(amqpAdmin.getQueueInfo(Messaging.INVOICE_DLQ).getMessageCount()).isZero();
        assertThat(amqpAdmin.getQueueInfo(Messaging.AUDIT_DLQ).getMessageCount()).isZero();
    }

    @Test
    void rejectedWorkMessage_isDeadLetteredToTheSameWorkersDlq() {
        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", "poison");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(amqpAdmin.getQueueInfo(Messaging.INVOICE_QUEUE).getMessageCount()).isEqualTo(1));

        // exactly what a failing listener causes: take the message, reject it, do not requeue
        rabbitTemplate.execute(channel -> {
            GetResponse response = channel.basicGet(Messaging.INVOICE_QUEUE, false);
            channel.basicNack(response.getEnvelope().getDeliveryTag(), false, false);
            return null;
        });

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(amqpAdmin.getQueueInfo(Messaging.INVOICE_DLQ).getMessageCount()).isEqualTo(1));
        assertThat(amqpAdmin.getQueueInfo(Messaging.INVOICE_QUEUE).getMessageCount()).isZero();
        // the other workers' copies are untouched
        assertThat(amqpAdmin.getQueueInfo(Messaging.EMAIL_QUEUE).getMessageCount()).isEqualTo(1);
        assertThat(amqpAdmin.getQueueInfo(Messaging.EMAIL_DLQ).getMessageCount()).isZero();
    }
}

