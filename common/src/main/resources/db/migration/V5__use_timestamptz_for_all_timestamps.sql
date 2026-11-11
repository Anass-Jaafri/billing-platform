-- Converts the zone-less TIMESTAMP columns from V1-V3 to TIMESTAMPTZ (ADR-009).
-- Existing values are interpreted as UTC.

ALTER TABLE charges
    ALTER COLUMN created_date       TYPE TIMESTAMPTZ USING created_date AT TIME ZONE 'UTC',
    ALTER COLUMN last_modified_date TYPE TIMESTAMPTZ USING last_modified_date AT TIME ZONE 'UTC';

ALTER TABLE webhook_events
    ALTER COLUMN processed_at        TYPE TIMESTAMPTZ USING processed_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_date        TYPE TIMESTAMPTZ USING created_date AT TIME ZONE 'UTC',
    ALTER COLUMN last_modified_date  TYPE TIMESTAMPTZ USING last_modified_date AT TIME ZONE 'UTC';

ALTER TABLE invoices
    ALTER COLUMN created_date       TYPE TIMESTAMPTZ USING created_date AT TIME ZONE 'UTC',
    ALTER COLUMN last_modified_date TYPE TIMESTAMPTZ USING last_modified_date AT TIME ZONE 'UTC';