package com.anass.billing.api.metrics;

import com.anass.billing.common.messaging.DlqWorker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Component;

/**
 * Live message counts for every work queue and dead-letter queue.
 * Spring Boot binds every MeterBinder bean to the registry automatically.
 */
@Component
public class QueueDepthMetrics implements MeterBinder {

    public static final String METRIC = "billing.queue.messages";

    private static final Logger log = LoggerFactory.getLogger(QueueDepthMetrics.class);

    private final AmqpAdmin amqpAdmin;

    public QueueDepthMetrics(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (DlqWorker worker : DlqWorker.values()) {
            register(registry, worker, worker.getWorkQueue(), "work");
            register(registry, worker, worker.getDeadLetterQueue(), "dead-letter");
        }
    }

    private void register(MeterRegistry registry, DlqWorker worker, String queue, String kind) {
        Gauge.builder(METRIC, () -> depth(queue))
                .description("Messages currently waiting in a RabbitMQ queue (NaN = queue missing or broker unreachable)")
                .baseUnit("messages")
                .tag("worker", worker.getPathName())
                .tag("queue", queue)
                .tag("kind", kind)
                .register(registry);
    }

    // Called each time the metric is read, not on a timer
    private double depth(String queue) {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(queue);
            return info == null ? Double.NaN : info.getMessageCount();
        } catch (RuntimeException e) {
            log.debug("Could not read depth of queue {}: {}", queue, e.getMessage());
            return Double.NaN;
        }
    }
}