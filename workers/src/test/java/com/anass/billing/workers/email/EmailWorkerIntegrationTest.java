package com.anass.billing.workers.email;

import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.messaging.Messaging;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class EmailWorkerIntegrationTest {

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
    }

    @Test
    void confirmedEvent_sendsReceiptToCustomer() {
        Charge charge = savedCharge("cust_receipt");

        publish(ChargeEvent.confirmed(charge));

        ArgumentCaptor<Receipt> sent = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptSender, timeout(10_000)).send(sent.capture());
        assertThat(sent.getValue().recipient()).isEqualTo("cust_receipt");
        assertThat(sent.getValue().body()).contains(charge.getId().toString());
    }

    @Test
    void failedEvent_sendsNoReceipt() {
        Charge failed = savedCharge("cust_failed");
        Charge barrier = savedCharge("cust_barrier");

        publish(ChargeEvent.failed(failed));
        publish(ChargeEvent.confirmed(barrier));

        // the barrier receipt arriving proves the earlier failed event was already handled
        verify(receiptSender, timeout(10_000)).send(argThat(r -> r.recipient().equals("cust_barrier")));
        verify(receiptSender, never()).send(argThat(r -> r.recipient().equals("cust_failed")));
    }

    private Charge savedCharge(String customerRef) {
        return chargeRepository.saveAndFlush(Charge.builder()
                .customerRef(customerRef).amount(5000).currency("MAD").build());
    }

    private void publish(ChargeEvent event) {
        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", event);
    }
}
