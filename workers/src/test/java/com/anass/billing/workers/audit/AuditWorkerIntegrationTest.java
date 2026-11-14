package com.anass.billing.workers.audit;

import com.anass.billing.common.audit.AuditLog;
import com.anass.billing.common.audit.AuditLogRepository;
import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


@SpringBootTest
@Testcontainers
class AuditWorkerIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    ChargeRepository chargeRepository;
    @Autowired
    AuditLogRepository auditLogRepository;

    @DynamicPropertySource
    static void connectToContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }

    @Test
    void confirmedEvent_isRecordedWithFullPayload() {
        Charge charge = savedCharge();
        ChargeEvent event = ChargeEvent.confirmed(charge);

        publish(event);

        awaitAudited(event);
        AuditLog entry = auditLogRepository.findByChargeIdOrderByRecordedAtAsc(charge.getId()).get(0);
        assertThat(entry.getEventType()).isEqualTo("charge.confirmed");
        assertThat(entry.getPayload()).contains(charge.getId().toString()).contains("cust_audit");
    }

    @Test
    void failedEvent_isRecordedToo() {
        Charge charge = savedCharge();
        ChargeEvent event = ChargeEvent.failed(charge);

        publish(event);

        awaitAudited(event);
        assertThat(auditLogRepository.findByChargeIdOrderByRecordedAtAsc(charge.getId()))
                .extracting(AuditLog::getEventType).containsExactly("charge.failed");
    }

    @Test
    void duplicateEvent_isRecordedOnlyOnce() {
        Charge charge = savedCharge();
        Charge other = savedCharge();
        ChargeEvent event = ChargeEvent.confirmed(charge);
        ChargeEvent barrier = ChargeEvent.confirmed(other);

        publish(event);
        publish(event);      // redelivery: same eventId
        publish(barrier);
        awaitAudited(barrier); // one consumer thread: the duplicate has been handled by now

        assertThat(auditLogRepository.findByChargeIdOrderByRecordedAtAsc(charge.getId())).hasSize(1);
    }

    // --- helpers ---

    private Charge savedCharge() {
        return chargeRepository.saveAndFlush(Charge.builder()
                .customerRef("cust_audit").amount(5000).currency("MAD").build());
    }

    private void publish(ChargeEvent event) {
        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", event);
    }

    private void awaitAudited(ChargeEvent event) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(auditLogRepository.existsByEventId(event.eventId())).isTrue());
    }
}