package com.anass.billing.workers;

import com.anass.billing.common.audit.AuditLogRepository;
import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.invoice.InvoiceRepository;
import com.anass.billing.common.messaging.Messaging;
import com.anass.billing.workers.email.ReceiptSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class WorkerIsolationIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String FAILING_CUSTOMER = "cust_email_fails";
    @MockBean
    ReceiptSender receiptSender;
    @Autowired
    RabbitTemplate rabbitTemplate;
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
        // A failed message is dropped instead of being redelivered forever.
        // Real retry + dead-letter handling arrives with the Resilience epic.
        registry.add("spring.rabbitmq.listener.simple.default-requeue-rejected", () -> "false");
    }

    @BeforeEach
    void emailFailsForOneCustomer() {
        doThrow(new RuntimeException("SMTP is down"))
                .when(receiptSender).send(argThat(r -> FAILING_CUSTOMER.equals(r.recipient())));
    }

    @Test
    void failingEmailWorker_doesNotAffectInvoiceAndAuditWorkers() {
        Charge charge = savedCharge(FAILING_CUSTOMER);
        ChargeEvent event = ChargeEvent.confirmed(charge);

        publish(event);

        // 1. the email worker really ran, and failed (once: the message was dropped, not looped)
        verify(receiptSender, timeout(10_000)).send(argThat(r -> FAILING_CUSTOMER.equals(r.recipient())));

        // 2. yet the other two workers fully processed the very same event
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(invoiceRepository.existsByChargeId(charge.getId())).isTrue();
            assertThat(auditLogRepository.existsByEventId(event.eventId())).isTrue();
        });
        verify(receiptSender, times(1)).send(argThat(r -> FAILING_CUSTOMER.equals(r.recipient())));
    }

    @Test
    void failedEmail_doesNotBlockLaterEventsOnTheSameQueue() {
        Charge failing = savedCharge(FAILING_CUSTOMER);
        Charge healthy = savedCharge("cust_email_works");

        publish(ChargeEvent.confirmed(failing));
        publish(ChargeEvent.confirmed(healthy));

        // the email worker keeps consuming after the failure and delivers the next receipt
        verify(receiptSender, timeout(10_000)).send(argThat(r -> "cust_email_works".equals(r.recipient())));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(invoiceRepository.existsByChargeId(failing.getId())).isTrue();
            assertThat(invoiceRepository.existsByChargeId(healthy.getId())).isTrue();
        });
    }

    // --- helpers ---

    private Charge savedCharge(String customerRef) {
        return chargeRepository.saveAndFlush(Charge.builder()
                .customerRef(customerRef).amount(5000).currency("MAD").build());
    }

    private void publish(ChargeEvent event) {
        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", event);
    }
}