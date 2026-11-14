package com.anass.billing.workers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageConversionConfig {
    // Spring Boot applies this bean to the RabbitTemplate and to every @RabbitListener.
    // It reuses Boot's own ObjectMapper so dates are written and read the same way
    // as in api (ISO text, not arrays).

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter(ObjectMapper objectMapper){
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
