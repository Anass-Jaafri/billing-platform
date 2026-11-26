package com.anass.billing.common.correlation;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;

public class CorrelationIdListenerAdvice implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        String id = null;
        for (Object arg : invocation.getArguments()) {
            if (arg instanceof Message message) {
                id = message.getMessageProperties().getCorrelationId();
                break;
            }
        }
        if (!CorrelationId.isSafe(id)) {
            id = CorrelationId.newId();   // message without a usable ID still gets one
        }
        MDC.put(CorrelationId.MDC_KEY, id);
        try {
            return invocation.proceed();
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}