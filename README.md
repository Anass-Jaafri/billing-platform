# Billing & Payment Orchestration Platform

An event-driven billing/payment orchestration service built to demonstrate
production-grade backend patterns — idempotent webhook handling, retries with
backoff, dead-letter queues, and observability — rather than another CRUD app.

Full project context lives in:

- [`PRD.md`](./PRD.md) — problem statement, goals, and success criteria
- [`TDD_and_ADRs.md`](./TDD_and_ADRs.md) — architecture, data model, and the
  reasoning behind each major technical decision
- [`BACKLOG.md`](./BACKLOG.md) — the full ticket breakdown this project is
  being built against

## Architecture

> Diagram to be added here once the core flow (webhook → workers) is
> implemented — see TDD_and_ADRs.md Section 2 for the current design.

## Modules

| Module | Purpose |
|---|---|
| `common` | Shared JPA entities, event DTOs, and messaging constants used by both `api` and `workers` |
| `api` | REST API: charge lifecycle, webhook ingestion, auth, admin/DLQ endpoints. Publishes events. |
| `workers` | RabbitMQ consumers: invoice, email, audit workers, with Resilience4j retry + DLQ handling |

## Running locally

Requires: Java 17, Maven, Docker.

```bash
# 1. Start infrastructure (Postgres, RabbitMQ, Redis)
docker compose up -d

# 2. Build all modules
mvn clean install

# 3. Run the API (once CHG-* tickets are implemented)
cd api && mvn spring-boot:run

# 4. Run the workers (once WRK-* tickets are implemented)
cd workers && mvn spring-boot:run
```

RabbitMQ management UI: http://localhost:15672 (guest/guest)

## Status

This project is being built ticket-by-ticket against `BACKLOG.md`, starting
with the Infra & Repo Setup epic. Functional endpoints don't exist yet —
check the backlog for current progress.

## How idempotency & retry work

> To be filled in once WH-* and RES-* epics are implemented (OBS-6) — will
> link back to ADR-002 and ADR-003 in `TDD_and_ADRs.md`.
