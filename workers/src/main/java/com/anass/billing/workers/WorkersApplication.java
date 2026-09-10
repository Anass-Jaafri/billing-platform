package com.anass.billing.workers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the RabbitMQ consumer service.
 *
 * <p>Runs the invoice, email, and audit workers (WRK-*) as a deliberately
 * separate deployable from the api module — see TDD_and_ADRs.md ADR-001.
 * This module never runs Flyway migrations itself; the api module owns the
 * schema, workers only reads/writes through it.
 */
@SpringBootApplication(scanBasePackages = "com.anass.billing")
public class WorkersApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkersApplication.class, args);
    }
}
