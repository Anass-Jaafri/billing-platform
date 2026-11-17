package com.anass.billing.workers.config;

import com.anass.billing.common.messaging.Messaging;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQTopologyConfig {

    @Bean
    public FanoutExchange chargeEventsExchange() {
        return new FanoutExchange(Messaging.CHARGE_EVENTS_EXCHANGE);
    }

    // Where rejected (exhausted) messages go. Direct = exact routing-key match
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(Messaging.DEAD_LETTER_EXCHANGE);
    }

    // --- work queues: durable, each dead-letters to the DLX using its own name as key ---

    @Bean
    public Queue invoiceQueue() {
        return workQueue(Messaging.INVOICE_QUEUE);
    }

    @Bean
    public Queue emailQueue() {
        return workQueue(Messaging.EMAIL_QUEUE);
    }

    @Bean
    public Queue auditQueue() {
        return workQueue(Messaging.AUDIT_QUEUE);
    }

    // --- one dead-letter queue per worker ---

    @Bean
    public Queue invoiceDlq() {
        return QueueBuilder.durable(Messaging.INVOICE_DLQ).build();
    }

    @Bean
    public Queue emailDlq() {
        return QueueBuilder.durable(Messaging.EMAIL_DLQ).build();
    }

    @Bean
    public Queue auditDlq() {
        return QueueBuilder.durable(Messaging.AUDIT_DLQ).build();
    }

    // --- bindings: events fan out into the work queues ---

    @Bean
    public Binding invoiceBinding() {
        return BindingBuilder.bind(invoiceQueue()).to(chargeEventsExchange());
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(chargeEventsExchange());
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(chargeEventsExchange());
    }

    // --- bindings: dead letters route to the matching DLQ by the work queue's name ---

    @Bean
    public Binding invoiceDlqBinding() {
        return BindingBuilder.bind(invoiceDlq()).to(deadLetterExchange()).with(Messaging.INVOICE_QUEUE);
    }

    @Bean
    public Binding emailDlqBinding() {
        return BindingBuilder.bind(emailDlq()).to(deadLetterExchange()).with(Messaging.EMAIL_QUEUE);
    }

    @Bean
    public Binding auditDlqBinding() {
        return BindingBuilder.bind(auditDlq()).to(deadLetterExchange()).with(Messaging.AUDIT_QUEUE);
    }

    private Queue workQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(Messaging.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(name)
                .build();
    }
}
