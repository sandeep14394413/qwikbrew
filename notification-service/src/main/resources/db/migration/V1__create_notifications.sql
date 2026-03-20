CREATE TABLE IF NOT EXISTS in_app_notifications (
    id             VARCHAR(36)  PRIMARY KEY,
    user_id        VARCHAR(36)  NOT NULL,
    title          VARCHAR(255) NOT NULL,
    body           TEXT,
    reference_id   VARCHAR(100),
    reference_type VARCHAR(50),
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notif_user ON in_app_notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notif_read ON in_app_notifications(user_id, is_read);
