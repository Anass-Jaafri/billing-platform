package com.anass.billing.workers.config;

import com.anass.billing.common.messaging.Messaging;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;

public class RabbitMQTopologyConfig {

    @Bean
    public FanoutExchange chargeEventsExchange(){
        return new FanoutExchange(Messaging.CHARGE_EVENTS_EXCHANGE);
    }
    // Durable: the queue (and the messages in it) survive a RabbitMQ restart
    @Bean
    public Queue invoiceQueue(){
        return QueueBuilder.durable(Messaging.INVOICE_QUEUE).build();
    }

    @Bean
    public Queue emailQueue(){
        return QueueBuilder.durable(Messaging.EMAIL_QUEUE).build();
    }

    @Bean
    public Queue auditQueue(){
        return QueueBuilder.durable(Messaging.AUDIT_QUEUE).build();
    }

    // Durable: the queue (and the messages in it) survive a RabbitMQ restart
    @Bean
    public Binding invoicBinding(){
        return BindingBuilder.bind(invoiceQueue()).to(chargeEventsExchange());
    }
    @Bean
    public Binding emailBinding(){
        return BindingBuilder.bind(emailQueue()).to(chargeEventsExchange());
    }
    @Bean
    public Binding auditBinding(){
        return BindingBuilder.bind(auditQueue()).to(chargeEventsExchange());
    }
}
