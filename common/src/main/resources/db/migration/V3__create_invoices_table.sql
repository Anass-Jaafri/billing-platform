CREATE SEQUENCE invoice_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE invoices
(
    id                 UUID PRIMARY KEY,
    charge_id          UUID        NOT NULL,
    invoice_number     VARCHAR(20) NOT NULL,
    generated_at       TIMESTAMP   NOT NULL,
    created_date       TIMESTAMP   NOT NULL,
    last_modified_date TIMESTAMP,

    CONSTRAINT fk_invoices_charge FOREIGN KEY (charge_id) REFERENCES charges (id),

    CONSTRAINT uq_invoices_charge_id UNIQUE (charge_id),
    CONSTRAINT uq_invoices_invoice_number UNIQUE (invoice_number)
);