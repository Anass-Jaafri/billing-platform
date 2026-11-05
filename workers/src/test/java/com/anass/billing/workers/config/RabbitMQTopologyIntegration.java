package com.anass.billing.workers.config;


import com.anass.billing.common.messaging.Messaging;
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

    @DynamicPropertySource
    static void connectToContainers(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    }
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;

    @Test
    void oneEvent_isCopiedIntoEveryWorkerQueue(){
        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE,"","one event");

        for (String queue : List.of(Messaging.INVOICE_QUEUE, Messaging.EMAIL_QUEUE, Messaging.AUDIT_QUEUE)){
            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(()->
                            assertThat(amqpAdmin.getQueueInfo(queue).getMessageCount()).isEqualTo(1));
        }
    }
}

