CREATE TABLE transactions
(
    id                UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    idempotency_key   VARCHAR(255)  NOT NULL UNIQUE,
    from_account_id   UUID          REFERENCES accounts(id),
    to_account_id     UUID          REFERENCES accounts(id),
    amount            DECIMAL(19,4) NOT NULL,
    currency          VARCHAR(3)    NOT NULL DEFAULT 'INR',
    type              VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    description       VARCHAR(255),
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMP
);

CREATE INDEX ix_transactions_idempotency_key ON transactions (idempotency_key);
CREATE INDEX ix_transactions_from_account    ON transactions (from_account_id);
CREATE INDEX ix_transactions_to_account      ON transactions (to_account_id);
CREATE INDEX ix_transactions_status          ON transactions (status);