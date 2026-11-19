package com.anass.billing.common.dlq;

import com.anass.billing.common.messaging.*;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Testcontainers
class DlqOperationsIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static final List<DlqWorker> WORKERS = List.of(DlqWorker.INVOICE, DlqWorker.EMAIL, DlqWorker.AUDIT);

    private CachingConnectionFactory connectionFactory;
    private RabbitAdmin admin;
    private RabbitTemplate template;
    private DlqOperations operations;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory(rabbit.getHost(), rabbit.getAmqpPort());
        admin = new RabbitAdmin(connectionFactory);
        template = new RabbitTemplate(connectionFactory);
        operations = new DlqOperations(connectionFactory);

        // same shape as the real topology: work queue --(reject)--> DLX --> <queue>.dlq
        admin.declareExchange(new DirectExchange(Messaging.DEAD_LETTER_EXCHANGE));
        DirectExchange dlx = new DirectExchange(Messaging.DEAD_LETTER_EXCHANGE);
        for (DlqWorker worker : WORKERS) {
            Queue workQueue = QueueBuilder.durable(worker.getWorkQueue())
                    .deadLetterExchange(Messaging.DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(worker.getWorkQueue())
                    .build();
            Queue dlq = new Queue(worker.getDeadLetterQueue(), true);
            admin.declareQueue(workQueue);
            admin.declareQueue(dlq);
            admin.declareBinding(BindingBuilder.bind(dlq).to(dlx).with(worker.getWorkQueue()));
            admin.purgeQueue(worker.getWorkQueue());
            admin.purgeQueue(worker.getDeadLetterQueue());
        }
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void list_returnsMessagesWithPayloadAndFailureCount() {
        deadLetterOnEmail("m-1", "{\"eventType\":\"charge.confirmed\"}");
        deadLetterOnEmail("m-2", "{\"eventType\":\"charge.confirmed\"}");

        List<DlqMessage> messages = operations.list(DlqWorker.EMAIL, 50);

        assertThat(messages).extracting(DlqMessage::messageId).containsExactlyInAnyOrder("m-1", "m-2");
        assertThat(messages).allSatisfy(m -> {
            assertThat(m.failureCount()).isEqualTo(1);           // dead-lettered once by the broker
            assertThat(m.payload()).contains("charge.confirmed");
        });
    }

    @Test
    void list_doesNotRemoveMessages() {
        deadLetterOnEmail("m-1", "{}");
        deadLetterOnEmail("m-2", "{}");

        operations.list(DlqWorker.EMAIL, 50);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(admin.getQueueInfo(Messaging.EMAIL_DLQ).getMessageCount()).isEqualTo(2));
        assertThat(operations.list(DlqWorker.EMAIL, 50)).hasSize(2);   // and still listable
    }

    @Test
    void list_respectsTheLimit_andAnEmptyDlqGivesAnEmptyList() {
        assertThat(operations.list(DlqWorker.AUDIT, 50)).isEmpty();

        deadLetterOnEmail("m-1", "{}");
        deadLetterOnEmail("m-2", "{}");
        deadLetterOnEmail("m-3", "{}");

        assertThat(operations.list(DlqWorker.EMAIL, 2)).hasSize(2);
    }

    @Test
    void replay_movesTheMessageToThatWorkersQueueOnly() {
        deadLetterOnEmail("m-1", "{\"n\":1}");
        deadLetterOnEmail("m-2", "{\"n\":2}");

        operations.replay(DlqWorker.EMAIL, "m-1");

        Message replayed = template.receive(Messaging.EMAIL_QUEUE, 5_000);
        assertThat(replayed).isNotNull();
        assertThat(replayed.getMessageProperties().getMessageId()).isEqualTo("m-1");
        assertThat(new String(replayed.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"n\":1}");

        // the other message stays in the DLQ; the other workers' queues are untouched
        assertThat(operations.list(DlqWorker.EMAIL, 50)).extracting(DlqMessage::messageId).containsExactly("m-2");
        assertThat(admin.getQueueInfo(Messaging.INVOICE_QUEUE).getMessageCount()).isZero();
        assertThat(admin.getQueueInfo(Messaging.AUDIT_QUEUE).getMessageCount()).isZero();
    }

    @Test
    void replay_ofAnUnknownMessageId_throwsAndChangesNothing() {
        deadLetterOnEmail("m-1", "{}");

        assertThatThrownBy(() -> operations.replay(DlqWorker.EMAIL, "no-such-id"))
                .isInstanceOf(DlqMessageNotFoundException.class);

        assertThat(operations.list(DlqWorker.EMAIL, 50)).hasSize(1);
        assertThat(admin.getQueueInfo(Messaging.EMAIL_QUEUE).getMessageCount()).isZero();
    }

    // The experiment: does the broker keep counting when WE republish a dead-lettered message?
    @Test
    void replay_thenFailsAgain_countGrows() {
        deadLetterOnEmail("m-1", "{}");
        operations.replay(DlqWorker.EMAIL, "m-1");

        rejectOneFrom(Messaging.EMAIL_QUEUE);                       // the worker fails it again
        awaitDlqDepth(1);

        assertThat(operations.list(DlqWorker.EMAIL, 50))
                .singleElement()
                .satisfies(m -> assertThat(m.failureCount()).isEqualTo(2));
    }

    // --- helpers ---

    /**
     * Publishes to the email work queue, then rejects it so the BROKER dead-letters it (real x-death).
     */
    private void deadLetterOnEmail(String messageId, String json) {
        int before = dlqDepth();
        MessageProperties props = new MessageProperties();
        props.setMessageId(messageId);
        props.setContentType("application/json");
        template.send("", Messaging.EMAIL_QUEUE, new Message(json.getBytes(StandardCharsets.UTF_8), props));

        rejectOneFrom(Messaging.EMAIL_QUEUE);
        awaitDlqDepth(before + 1);
    }

    /**
     * What a failing listener does: take one message and reject it without requeue.
     */
    private void rejectOneFrom(String queue) {
        template.execute(channel -> {
            GetResponse response = channel.basicGet(queue, false);
            assertThat(response).as("a message in " + queue).isNotNull();
            channel.basicNack(response.getEnvelope().getDeliveryTag(), false, false);
            return null;
        });
    }

    private int dlqDepth() {
        return admin.getQueueInfo(Messaging.EMAIL_DLQ).getMessageCount();
    }

    private void awaitDlqDepth(int expected) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(dlqDepth()).isEqualTo(expected));
    }
}