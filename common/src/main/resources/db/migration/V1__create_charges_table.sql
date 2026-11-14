
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

CREATE INDEX idx_charges_customer_ref ON charges (customer_ref);
