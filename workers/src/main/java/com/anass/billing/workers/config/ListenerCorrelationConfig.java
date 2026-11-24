package com.anass.billing.workers.config;

import com.anass.billing.common.correlation.CorrelationIdListenerAdvice;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ListenerCorrelationConfig {

    @Bean
    ContainerCustomizer<SimpleMessageListenerContainer> correlationIdContainerCustomizer() {
        return container -> container.setAdviceChain(new Advice[]{new CorrelationIdListenerAdvice()});
    }
}