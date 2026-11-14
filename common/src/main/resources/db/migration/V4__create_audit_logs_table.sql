CREATE TABLE audit_logs(
    id  UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    charge_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    recorded_at TIMESTAMP NOT NULL,

    -- A redelivered event hits this and is detected by the database
    CONSTRAINT uq_audit_logs_event_id UNIQUE (event_id)
);
CREATE INDEX idx_audit_logs_charge_id ON audit_logs (charge_id);

-- Append-only, enforced by the database itself
CREATE FUNCTION audit_logs_reject_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_modification();