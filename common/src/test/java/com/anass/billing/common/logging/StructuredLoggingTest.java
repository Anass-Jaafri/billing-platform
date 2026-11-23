package com.anass.billing.common.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.MDC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void logWith(String profile, Runnable logging) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .properties("spring.application.name=log-test", "spring.main.banner-mode=off");
        if (profile != null) {
            builder.profiles(profile);
        }
        try (ConfigurableApplicationContext ignored = builder.run()) {   // closing the context resets logging
            logging.run();
        }
    }

    @Test
    void prodProfile_writesOneJsonObjectPerLine_withMdcAndAppFields(CapturedOutput output) throws Exception {
        logWith("prod", () -> {
            MDC.put("correlationId", "abc-123");
            try {
                LoggerFactory.getLogger("test.logger").info("hello json");
            } finally {
                MDC.clear();
            }
        });

        JsonNode line = jsonLineWithMessage(output, "hello json");
        assertThat(line.get("level").asText()).isEqualTo("INFO");
        assertThat(line.get("logger_name").asText()).isEqualTo("test.logger");
        assertThat(line.get("correlationId").asText()).isEqualTo("abc-123");
        assertThat(line.get("app").asText()).isEqualTo("log-test");
    }

    @Test
    void prodProfile_putsAnExceptionIntoOneStackTraceField(CapturedOutput output) throws Exception {
        logWith("prod", () -> LoggerFactory.getLogger("test.logger")
                .error("it broke", new IllegalStateException("boom")));

        JsonNode line = jsonLineWithMessage(output, "it broke");
        assertThat(line.get("stack_trace").asText()).contains("IllegalStateException").contains("boom");
    }

    @Test
    void defaultProfile_staysReadableText(CapturedOutput output) {
        logWith(null, () -> LoggerFactory.getLogger("test.logger").info("hello text"));

        String line = output.getAll().lines().filter(l -> l.contains("hello text")).findFirst().orElseThrow();
        assertThat(line).doesNotStartWith("{");
    }

    private JsonNode jsonLineWithMessage(CapturedOutput output, String message) throws Exception {
        for (String line : output.getAll().split("\\R")) {
            if (line.startsWith("{")) {
                JsonNode node = objectMapper.readTree(line);
                if (message.equals(node.path("message").asText())) {
                    return node;
                }
            }
        }
        throw new AssertionError("No JSON log line with message '" + message + "' in:\n" + output.getAll());
    }

    @Configuration
    static class Empty {
    }
}
