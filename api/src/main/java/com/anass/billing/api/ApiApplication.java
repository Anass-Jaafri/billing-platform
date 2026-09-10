package com.anass.billing.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the REST API service.
 *
 * <p>Scope owned by this module (per BACKLOG.md): charge lifecycle (CHG-*),
 * webhook ingestion (WH-*), auth (AUTH-*), and admin/DLQ endpoints
 * (RES-4, RES-5). This module is the event <em>publisher</em>; the workers
 * module is the event <em>consumer</em> — see TDD_and_ADRs.md Section 2 for
 * the full architecture diagram.
 */
@SpringBootApplication(scanBasePackages = "com.anass.billing")
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
