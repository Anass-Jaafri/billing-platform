package com.anass.billing.workers.config;

import com.anass.billing.common.event.ChargeEvent;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
@Import(MessageConversionConfig.class)

public class MessageConversionConfigTest {

    @Autowired private Jackson2JsonMessageConverter converter;

    private ChargeEvent sampleEvent(){
       return new ChargeEvent(
               "charge.confirmed",
               UUID.randomUUID(),
               LocalDateTime.of(2026,11,4,10,15,30,123_000_000),
               new ChargeEvent.ChargeEventData(UUID.randomUUID(),5000,"USD","cust_123"));
    }
    @Test
    void event_isWrittenAsJsonText_notJavaSerialization() {
        Message message = converter.toMessage(sampleEvent(), new MessageProperties());

        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");

        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"eventType\":\"charge.confirmed\"");
        assertThat(body).contains("\"currency\":\"USD\"");
        // ISO text: if this ever becomes [2026,11,4,...], api and workers would disagree
        assertThat(body).contains("\"occurredAt\":\"2026-11-04T10:15:30.123\"");
    }

    @Test
    void event_survivesTheRoundTrip() {
        ChargeEvent event = sampleEvent();
        Message message = converter.toMessage(event, new MessageProperties());

        Object rebuilt = converter.fromMessage(message, ParameterizedTypeReference.forType(ChargeEvent.class));

        assertThat(rebuilt).isEqualTo(event);
    }
}
