-- Usernames are stored in lower case. Together with the existing unique constraint
-- (uq_users_username) this makes them case-insensitive: 'Admin' and 'admin' cannot coexist.
UPDATE users SET username = lower(username) WHERE username <> lower(username);

ALTER TABLE users
    ADD CONSTRAINT ck_users_username_lowercase CHECK (username = lower(username));