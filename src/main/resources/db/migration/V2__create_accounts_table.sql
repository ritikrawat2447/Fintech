CREATE TABLE accounts
(
    id         UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users(id),
    balance    DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    currency   VARCHAR(3)    NOT NULL DEFAULT 'INR',
    status     VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_accounts_user_id ON accounts (user_id);