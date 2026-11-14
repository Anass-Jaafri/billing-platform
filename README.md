# Billing & Payment Orchestration Platform

An event-driven billing/payment orchestration service built to demonstrate
production-grade backend patterns — idempotent webhook handling, retries with
backoff, dead-letter queues, and observability — rather than another CRUD app.

## Modules

| Module    | Purpose                                                                                     |
|-----------|---------------------------------------------------------------------------------------------|
| `common`  | Shared JPA entities, event DTOs, and messaging constants used by both `api` and `workers`   |
| `api`     | REST API: charge lifecycle, webhook ingestion, auth, admin/DLQ endpoints. Publishes events. |
| `workers` | RabbitMQ consumers: invoice, email, audit workers, with Resilience4j retry + DLQ handling   |

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
