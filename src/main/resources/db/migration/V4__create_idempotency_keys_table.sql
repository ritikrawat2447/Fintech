CREATE TABLE idempotency_keys
(
    key          VARCHAR(255) NOT NULL PRIMARY KEY,
    response     TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMP    NOT NULL DEFAULT NOW() + INTERVAL '24 hours'
);

CREATE INDEX ix_idempotency_expires_at ON idempotency_keys (expires_at);