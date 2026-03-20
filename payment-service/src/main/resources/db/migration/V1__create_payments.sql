CREATE TABLE IF NOT EXISTS wallet_balances (
    user_id VARCHAR(36)   PRIMARY KEY,
    balance NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    version BIGINT        NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id              VARCHAR(36)   PRIMARY KEY,
    user_id         VARCHAR(36)   NOT NULL,
    amount          NUMERIC(10,2) NOT NULL,
    type            VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    pay_method      VARCHAR(20),
    reference       VARCHAR(100),
    gateway_txn_id  VARCHAR(100),
    failure_reason  TEXT,
    created_at      TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_txn_user      ON wallet_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_txn_reference ON wallet_transactions(reference);
