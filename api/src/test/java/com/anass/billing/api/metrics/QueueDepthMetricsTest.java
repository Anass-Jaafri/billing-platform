package com.anass.billing.api.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;

import java.io.IOException;

import static com.anass.billing.api.metrics.QueueDepthMetrics.METRIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueDepthMetricsTest {

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private void bind() {
        new QueueDepthMetrics(amqpAdmin).bindTo(registry);
    }

    private double value(String queue) {
        return registry.get(METRIC).tag("queue", queue).gauge().value();
    }

    @Test
    void registersAWorkAndADeadLetterGaugePerWorker() {
        bind();

        assertThat(registry.find(METRIC).gauges()).hasSize(6);
        assertThat(registry.get(METRIC).tag("queue", "invoice-queue").gauge().getId().getTag("kind")).isEqualTo("work");
        assertThat(registry.get(METRIC).tag("queue", "invoice-queue.dlq").gauge().getId().getTag("kind")).isEqualTo("dead-letter");
        assertThat(registry.get(METRIC).tag("queue", "email-queue").gauge().getId().getTag("worker")).isEqualTo("email");
    }

    @Test
    void reportsTheBrokersCurrentMessageCount_everyTimeItIsRead() {
        when(amqpAdmin.getQueueInfo("invoice-queue")).thenReturn(new QueueInformation("invoice-queue", 7, 1));
        bind();
        assertThat(value("invoice-queue")).isEqualTo(7);

        when(amqpAdmin.getQueueInfo("invoice-queue")).thenReturn(new QueueInformation("invoice-queue", 2, 1));
        assertThat(value("invoice-queue")).isEqualTo(2);
    }

    @Test
    void aMissingQueue_isNaN_notZero() {
        when(amqpAdmin.getQueueInfo("audit-queue.dlq")).thenReturn(null);
        bind();

        assertThat(value("audit-queue.dlq")).isNaN();
    }

    @Test
    void anUnreachableBroker_isNaN_andDoesNotThrow() {
        when(amqpAdmin.getQueueInfo("email-queue")).thenThrow(new AmqpConnectException(new IOException("down")));
        bind();

        assertThat(value("email-queue")).isNaN();
    }
}