package com.anass.billing.workers.invoice;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.invoice.Invoice;
import com.anass.billing.common.invoice.InvoiceRepository;
import com.anass.billing.common.messaging.Messaging;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class InvoiceWorkerIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    ChargeRepository chargeRepository;
    @Autowired
    InvoiceRepository invoiceRepository;

    @DynamicPropertySource
    static void connectToContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @Test
    void confirmedEvent_createsInvoice() {
        Charge charge = savedCharge();

        publish(ChargeEvent.confirmed(charge));

        awaitInvoiceFor(charge.getId());
        assertThat(invoiceRepository.findByChargeId(charge.getId()))
                .get().extracting(Invoice::getInvoiceNumber).asString().startsWith("INV-");
    }

    @Test
    void duplicateEvent_doesNotConsumeASequenceNumber() {
        Charge a = savedCharge();
        Charge b = savedCharge();

        publish(ChargeEvent.confirmed(a));
        publish(ChargeEvent.confirmed(a));   // redelivery
        publish(ChargeEvent.confirmed(b));
        awaitInvoiceFor(b.getId());          // one consumer thread: by now the duplicate is handled

        assertThat(numberOf(b.getId())).isEqualTo(numberOf(a.getId()) + 1);
    }

    @Test
    void failedEvent_createsNoInvoice() {
        Charge failed = savedCharge();
        Charge barrier = savedCharge();

        publish(ChargeEvent.failed(failed));
        publish(ChargeEvent.confirmed(barrier));
        awaitInvoiceFor(barrier.getId());    // proves the failed event was already processed

        assertThat(invoiceRepository.existsByChargeId(failed.getId())).isFalse();
    }

    // --- helpers ---

    private Charge savedCharge() {
        return chargeRepository.saveAndFlush(Charge.builder()
                .customerRef("cust_123").amount(5000).currency("MAD").build());
    }

    private void publish(ChargeEvent event) {
        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", event);
    }

    private void awaitInvoiceFor(UUID chargeId) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(invoiceRepository.existsByChargeId(chargeId)).isTrue());
    }

    private long numberOf(UUID chargeId) {
        return Long.parseLong(invoiceRepository.findByChargeId(chargeId)
                .orElseThrow().getInvoiceNumber().substring("INV-".length()));
    }
}