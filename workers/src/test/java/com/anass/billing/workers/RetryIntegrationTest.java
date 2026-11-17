package com.anass.billing.workers;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.messaging.Messaging;
import com.anass.billing.workers.email.ReceiptSender;
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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class RetryIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @MockBean
    ReceiptSender receiptSender;
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    ChargeRepository chargeRepository;

    @DynamicPropertySource
    static void connectToContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        // Until the dead-letter queue exists (RES-3), an exhausted message is dropped.
        registry.add("spring.rabbitmq.listener.simple.default-requeue-rejected", () -> "false");
    }

    @Test
    void transientFailure_isRetriedWithBackoffUntilItSucceeds() {
        // fails twice, then works
        doThrow(new RuntimeException("temporary"))
                .doThrow(new RuntimeException("temporary"))
                .doNothing()
                .when(receiptSender).send(argThat(r -> "cust_flaky".equals(r.recipient())));
        Charge charge = savedCharge("cust_flaky");

        Instant start = Instant.now();
        publish(ChargeEvent.confirmed(charge));

        verify(receiptSender, timeout(15_000).times(3))
                .send(argThat(r -> "cust_flaky".equals(r.recipient())));
        // 1 s + 2 s of backoff between the three attempts (a little slack for timer precision)
        assertThat(Duration.between(start, Instant.now())).isGreaterThanOrEqualTo(Duration.ofMillis(2800));
    }

    @Test
    void permanentFailure_stopsAfterTheConfiguredNumberOfAttempts() {
        doThrow(new RuntimeException("always broken"))
                .when(receiptSender).send(argThat(r -> "cust_broken".equals(r.recipient())));
        Charge charge = savedCharge("cust_broken");

        publish(ChargeEvent.confirmed(charge));

        verify(receiptSender, timeout(15_000).times(3))
                .send(argThat(r -> "cust_broken".equals(r.recipient())));
        // and it does not keep going: still exactly 3 after waiting some more
        verify(receiptSender, after(3_000).times(3))
                .send(argThat(r -> "cust_broken".equals(r.recipient())));
    }

    private Charge savedCharge(String customerRef) {
        return chargeRepository.saveAndFlush(Charge.builder()
                .customerRef(customerRef).amount(5000).currency("MAD").build());
    }

    private void publish(ChargeEvent event) {
        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", event);
    }
}