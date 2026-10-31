package com.anass.billing.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    public static final String CHARGE_EVENTS_EXCHANGE = "charge-events";
    // Declared here (producer side)
    // workers will re-declare this same exchange when it adds queues/bindings;
    @Bean
    public FanoutExchange chargeEventsExchange(){
        return new FanoutExchange(CHARGE_EVENTS_EXCHANGE);
    }

    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper){
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // synchronizes this channel's commit with the surrounding
        // @Transactional method — if the DB transaction rolls back,
        // this publish never actually reaches the broker.
        template.setChannelTransacted(true);
        return template;
    }
}
