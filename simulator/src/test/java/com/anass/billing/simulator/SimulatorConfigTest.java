package com.anass.billing.simulator;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SimulatorConfigTest {
    private static final String CHARGE = "eb3560cf-a886-464d-baac-0f26662f5669";

    @Test
    void usesDefaultsWhenOnlyChargeIsGiven(){
        SimulatorConfig config = SimulatorConfig.parse(new String[] {"--charge", CHARGE});

        assertThat(config.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.outcome()).isEqualTo("SUCCEEDED");
        assertThat(config.delaySeconds()).isZero();
        assertThat(config.duplicates()).isZero();
        assertThat(config.simultaneous()).isFalse();
        assertThat(config.eventId()).startsWith("evt_");
    }

    @Test
    void readsAllOptions(){
        SimulatorConfig config = SimulatorConfig.parse(new String[] {
                "--charge", CHARGE, "--outcome", "FAILED", "--delay", "3",
                "--duplicates", "2", "--event-id", "evt_x", "--url", "http://host:9000"});

        assertThat(config.outcome()).isEqualTo("FAILED");
        assertThat(config.delaySeconds()).isEqualTo(3);
        assertThat(config.duplicates()).isEqualTo(2);
        assertThat(config.eventId()).isEqualTo("evt_x");
        assertThat(config.baseUrl()).isEqualTo("http://host:9000");
    }

    @Test
    void simultaneousIsASwitchWithNoValue() {
        SimulatorConfig config = SimulatorConfig.parse(
                new String[] {"--charge", CHARGE, "--simultaneous", "--duplicates", "1"});

        assertThat(config.simultaneous()).isTrue();
        assertThat(config.duplicates()).isEqualTo(1);
    }

    @Test
    void trailingSlashOnUrlIsRemoved() {
        SimulatorConfig config = SimulatorConfig.parse(
                new String[] {"--charge", CHARGE, "--url", "http://localhost:8080/"});

        assertThat(config.baseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void chargeIsRequired() {
        assertThatThrownBy(() -> SimulatorConfig.parse(new String[] {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--charge");
    }

    @Test
    void unknownOptionsAreRejected() {
        assertThatThrownBy(() -> SimulatorConfig.parse(new String[] {"--charge", CHARGE, "--duplicate", "2"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option");
    }
}
