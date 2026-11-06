CREATE TABLE webhook_events (
                                id                  UUID PRIMARY KEY,
                                provider_event_id   VARCHAR(255) NOT NULL UNIQUE,
                                charge_id           UUID NOT NULL REFERENCES charges (id),
                                payload             JSONB NOT NULL,
                                processed_at        TIMESTAMP,
                                created_date        TIMESTAMP NOT NULL,
                                last_modified_date  TIMESTAMP
);

CREATE INDEX idx_webhook_events_charge_id ON webhook_events (charge_id);