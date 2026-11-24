package com.anass.billing.workers.correlation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.anass.billing.common.charge.Charge;
import com.anass.billing.common.charge.ChargeRepository;
import com.anass.billing.common.correlation.CorrelationId;
import com.anass.billing.common.event.ChargeEvent;
import com.anass.billing.common.messaging.Messaging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class CorrelationIdWorkerIntegrationTest {

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String AUDIT_LOGGER = "com.anass.billing.workers.audit.AuditService";
    private final MdcCapturingAppender appender = new MdcCapturingAppender();
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

    @BeforeEach
    void attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(AUDIT_LOGGER);
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        ((Logger) LoggerFactory.getLogger(AUDIT_LOGGER)).detachAppender(appender);
        appender.stop();
    }

    @Test
    void messageWithACorrelationId_isProcessedUnderThatId() {
        ChargeEvent event = ChargeEvent.confirmed(savedCharge());

        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", event, message -> {
            message.getMessageProperties().setCorrelationId("corr-test-1");
            return message;
        });

        assertThat(awaitAuditedWithId(event)).isEqualTo("corr-test-1");
    }

    @Test
    void messageWithoutACorrelationId_getsAFreshOne() {
        ChargeEvent event = ChargeEvent.confirmed(savedCharge());

        rabbitTemplate.convertAndSend(Messaging.CHARGE_EVENTS_EXCHANGE, "", event);

        String id = awaitAuditedWithId(event);
        assertThat(CorrelationId.isSafe(id)).isTrue();
        assertThat(id).isNotEqualTo("corr-test-1");
    }

    // --- helpers ---

    private Charge savedCharge() {
        return chargeRepository.saveAndFlush(Charge.builder()
                .customerRef("cust_corr").amount(5000).currency("MAD").build());
    }

    /**
     * Waits for the audit log line of this event and returns the correlation ID the listener thread had.
     */
    private String awaitAuditedWithId(ChargeEvent event) {
        await().atMost(Duration.ofSeconds(10)).until(() -> lineFor(event).isPresent());
        return lineFor(event).orElseThrow()[1];
    }

    private Optional<String[]> lineFor(ChargeEvent event) {
        return appender.lines.stream()
                .filter(line -> line[0].contains(event.eventId().toString()) && line[0].startsWith("Audited"))
                .findFirst();
    }

    /**
     * Records {message, MDC correlationId} at the moment of logging, on the logging thread.
     */
    static class MdcCapturingAppender extends AppenderBase<ILoggingEvent> {
        final List<String[]> lines = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            lines.add(new String[]{event.getFormattedMessage(), MDC.get(CorrelationId.MDC_KEY)});
        }
    }
}