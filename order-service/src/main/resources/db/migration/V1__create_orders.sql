CREATE TABLE IF NOT EXISTS orders (
    id                    VARCHAR(36)   PRIMARY KEY,
    order_number          VARCHAR(30)   NOT NULL UNIQUE,
    user_id               VARCHAR(36)   NOT NULL,
    cafe_id               VARCHAR(36)   NOT NULL,
    subtotal              NUMERIC(10,2) NOT NULL,
    gst_amount            NUMERIC(10,2) NOT NULL,
    discount_amount       NUMERIC(10,2) NOT NULL DEFAULT 0,
    total_amount          NUMERIC(10,2) NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    payment_method        VARCHAR(20),
    payment_reference     VARCHAR(100),
    special_instructions  TEXT,
    estimated_minutes     INTEGER       DEFAULT 12,
    brew_points_earned    INTEGER,
    brew_points_redeemed  INTEGER,
    created_at            TIMESTAMP,
    accepted_at           TIMESTAMP,
    ready_at              TIMESTAMP,
    picked_up_at          TIMESTAMP
);
CREATE TABLE IF NOT EXISTS order_items (
    id               VARCHAR(36)   PRIMARY KEY,
    order_id         VARCHAR(36)   REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id     VARCHAR(36)   NOT NULL,
    item_name        VARCHAR(255)  NOT NULL,
    unit_price       NUMERIC(8,2)  NOT NULL,
    quantity         INTEGER       NOT NULL,
    line_total       NUMERIC(10,2) NOT NULL,
    customization    TEXT
);
CREATE TABLE IF NOT EXISTS order_item_addons (
    order_item_id VARCHAR(36) REFERENCES order_items(id) ON DELETE CASCADE,
    addon         VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_orders_user   ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_number ON orders(order_number);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
