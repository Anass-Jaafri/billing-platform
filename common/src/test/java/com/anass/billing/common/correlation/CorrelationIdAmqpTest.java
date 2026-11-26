package com.anass.billing.common.correlation;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrelationIdAmqpTest {

    private final CorrelationIdListenerAdvice advice = new CorrelationIdListenerAdvice();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    private Message messageWith(String correlationId) {
        MessageProperties props = new MessageProperties();
        props.setCorrelationId(correlationId);
        return new Message("{}".getBytes(), props);
    }

    private MethodInvocation invocationFor(Message message, AtomicReference<String> seen, Throwable toThrow)
            throws Throwable {
        MethodInvocation inv = mock(MethodInvocation.class);
        when(inv.getArguments()).thenReturn(new Object[]{message, null});
        when(inv.proceed()).thenAnswer(call -> {
            seen.set(MDC.get(CorrelationId.MDC_KEY));
            if (toThrow != null) throw toThrow;
            return null;
        });
        return inv;
    }

    @Test
    void publisher_copiesMdcIntoTheCorrelationIdProperty() {
        MDC.put(CorrelationId.MDC_KEY, "abc-1");
        Message out = new CorrelationIdMessagePostProcessor().postProcessMessage(messageWith(null));
        assertThat(out.getMessageProperties().getCorrelationId()).isEqualTo("abc-1");
    }

    @Test
    void publisher_leavesTheMessageAloneWhenThereIsNoMdcValue() {
        Message out = new CorrelationIdMessagePostProcessor().postProcessMessage(messageWith(null));
        assertThat(out.getMessageProperties().getCorrelationId()).isNull();
    }

    @Test
    void listener_restoresTheIdFromTheMessage_andClearsItAfter() throws Throwable {
        var seen = new AtomicReference<String>();
        advice.invoke(invocationFor(messageWith("abc-1"), seen, null));

        assertThat(seen.get()).isEqualTo("abc-1");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void listener_generatesAnIdWhenTheMessageHasNone() throws Throwable {
        var seen = new AtomicReference<String>();
        advice.invoke(invocationFor(messageWith(null), seen, null));

        assertThat(CorrelationId.isSafe(seen.get())).isTrue();
    }

    @Test
    void listener_clearsTheIdEvenWhenTheListenerThrows() throws Throwable {
        var seen = new AtomicReference<String>();
        var inv = invocationFor(messageWith("abc-1"), seen, new IllegalStateException("boom"));

        assertThatThrownBy(() -> advice.invoke(inv)).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }
}