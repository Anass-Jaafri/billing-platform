package com.anass.billing.common.correlation;

import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;

public class CorrelationIdMessagePostProcessor implements MessagePostProcessor {

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        String id = MDC.get(CorrelationId.MDC_KEY);
        if (id != null && message.getMessageProperties().getCorrelationId() == null) {
            message.getMessageProperties().setCorrelationId(id);
        }
        return message;
    }
}
