package com.anass.billing.workers;

import com.anass.billing.common.audit.AuditLogRepository;
import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.invoice.InvoiceRepository;
import com.anass.billing.common.messaging.Messaging;
import com.anass.billing.workers.email.ReceiptSender;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class PermanentFailureIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @MockBean
    ReceiptSender receiptSender;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    ChargeRepository chargeRepository;
    @Autowired
    InvoiceRepository invoiceRepository;
    @Autowired
    AuditLogRepository auditLogRepository;

    @DynamicPropertySource
    static void connectToContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        // no requeue setting here on purpose: this runs on the real application.yml
    }

    @Test
    void emailFailingEveryAttempt_endsInItsOwnDlq_andOtherWorkersAreUnaffected() {
        doThrow(new RuntimeException("SMTP is down")).when(receiptSender).send(any());
        Charge charge = chargeRepository.saveAndFlush(Charge.builder()
                .customerRef("cust_dead").amount(5000).currency("MAD").build());
        ChargeEvent event = ChargeEvent.confirmed(charge);

        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", event);

        // 1. after the third failed attempt (~3 s of backoff) the message reaches the email DLQ
        Message dead = rabbitTemplate.receive(Messaging.EMAIL_DLQ, 20_000);
        assertThat(dead).as("message in email DLQ").isNotNull();

        // 2. intact, and RabbitMQ recorded where it died and why
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8))
                .contains(event.eventId().toString())
                .contains(charge.getId().toString());
        Map<String, ?> death = dead.getMessageProperties().getXDeathHeader().get(0);
        assertThat(death.get("queue")).isEqualTo(Messaging.EMAIL_QUEUE);
        assertThat(death.get("reason")).isEqualTo("rejected");
        assertThat(death.get("count")).isEqualTo(1L);

        // 3. exactly three attempts, nothing looping, work queue empty
        verify(receiptSender, after(2_000).times(3)).send(any());
        assertThat(amqpAdmin.getQueueInfo(Messaging.EMAIL_QUEUE).getMessageCount()).isZero();

        // 4. the other workers finished the same event; only email's DLQ got a message
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(invoiceRepository.existsByChargeId(charge.getId())).isTrue();
            assertThat(auditLogRepository.existsByEventId(event.eventId())).isTrue();
        });
        assertThat(amqpAdmin.getQueueInfo(Messaging.INVOICE_DLQ).getMessageCount()).isZero();
        assertThat(amqpAdmin.getQueueInfo(Messaging.AUDIT_DLQ).getMessageCount()).isZero();
    }
}