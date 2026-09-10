/**
 * Shared domain code for the Billing & Payment Orchestration Platform.
 *
 * <p>Intended contents (populated as later backlog tickets are implemented,
 * not scaffolded ahead of time):
 * <ul>
 *   <li>{@code entity} — JPA entities shared between api and workers
 *       (Charge, WebhookEvent, Invoice, AuditLog — see TDD Section 3)</li>
 *   <li>{@code event} — event DTOs matching the schema in TDD Section 4
 *       (ChargeConfirmedEvent, ChargeFailedEvent)</li>
 *   <li>{@code messaging} — shared constants for exchange/queue names, so
 *       api (publisher) and workers (consumers) never hardcode strings that
 *       could drift out of sync</li>
 * </ul>
 */
package com.anass.billing.common;
