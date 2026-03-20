CREATE TABLE IF NOT EXISTS users (
    id             VARCHAR(36)    PRIMARY KEY,
    name           VARCHAR(255)   NOT NULL,
    email          VARCHAR(255)   NOT NULL UNIQUE,
    password_hash  VARCHAR(255)   NOT NULL,
    phone          VARCHAR(20)    NOT NULL,
    employee_id    VARCHAR(50),
    department     VARCHAR(100),
    floor          VARCHAR(20),
    wallet_balance NUMERIC(10,2)  NOT NULL DEFAULT 0.00,
    brew_points    INTEGER        NOT NULL DEFAULT 0,
    role           VARCHAR(20)    NOT NULL DEFAULT 'EMPLOYEE',
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_email       ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_employee_id ON users(employee_id);
