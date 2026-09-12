-- CHG-1: charges table
-- Mirrors com.anass.billing.common.charge.Charge. Keep these in sync —
-- if you add/rename a field on the entity, update this migration's
-- counterpart in a NEW migration file (never edit an already-applied one).

CREATE TABLE charges (
    id                  UUID PRIMARY KEY,
    customer_ref        VARCHAR(255) NOT NULL,
    amount              BIGINT NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    status              VARCHAR(20) NOT NULL
        CONSTRAINT chk_charges_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED')),
    created_date        TIMESTAMP NOT NULL,
    last_modified_date  TIMESTAMP
);

-- Charges are looked up by customer fairly often (e.g. future GET /charges
-- filtering by customer) — indexing now is cheap and avoids a slow sequential
-- scan later. Not indexing `status` yet: no query filters by status alone
-- in the current backlog, so that index would be speculative.
CREATE INDEX idx_charges_customer_ref ON charges (customer_ref);
