CREATE TABLE refresh_tokens (
    id                 UUID        PRIMARY KEY,
    jti                UUID        NOT NULL,
    user_id            UUID        NOT NULL,
    family_id          UUID        NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    revoked_at         TIMESTAMPTZ,
    created_date       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_modified_date TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_refresh_tokens_jti UNIQUE (jti),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);