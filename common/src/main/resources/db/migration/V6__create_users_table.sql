CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    username           VARCHAR(50)  NOT NULL,
    password_hash      VARCHAR(100) NOT NULL,
    role               VARCHAR(20)  NOT NULL,
    created_date       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_modified_date TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_username UNIQUE (username),
    -- The database refuses a role the application doesn't know about
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MERCHANT'))
);

-- DEVELOPMENT credentials only (ADR-011): admin / admin123, merchant / merchant123.
-- The values below are BCrypt hashes, never the passwords themselves.

INSERT INTO users (id, username, password_hash, role) VALUES
    (gen_random_uuid(), 'admin',    '$2a$10$wKYomCZPbIaL63N2ctjlu.yTN29v5z4lhVR8v/GUt0J1geuzseKNu', 'ADMIN'),
    (gen_random_uuid(), 'merchant', '$2a$10$zt5omH8Rh5Y1MUQFvz4cUeqhNTRqp3w9PVo0GSX5BthrSPAEZnBd.', 'MERCHANT');