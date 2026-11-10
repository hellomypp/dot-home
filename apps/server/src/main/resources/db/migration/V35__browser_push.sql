ALTER TABLE household_notification_preference ADD COLUMN quiet_start_minute INTEGER DEFAULT 1320;
ALTER TABLE household_notification_preference ADD COLUMN quiet_end_minute INTEGER DEFAULT 480;
ALTER TABLE household_notification_preference ADD COLUMN quiet_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Seoul';

CREATE TABLE browser_push_subscription (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    endpoint_hash VARCHAR(64) NOT NULL UNIQUE,
    endpoint VARCHAR(2048) NOT NULL,
    key_fingerprint VARCHAR(64) NOT NULL,
    session_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE
);
CREATE INDEX ix_push_user ON browser_push_subscription(user_id,session_hash);

CREATE TABLE browser_push_delivery (
    id VARCHAR(36) PRIMARY KEY,
    notification_id VARCHAR(36) NOT NULL,
    subscription_id VARCHAR(36) NOT NULL,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    attempts INTEGER NOT NULL,
    CONSTRAINT uq_push_delivery UNIQUE (notification_id,subscription_id),
    CONSTRAINT fk_push_delivery_notification FOREIGN KEY (notification_id) REFERENCES household_notification(id) ON DELETE CASCADE,
    CONSTRAINT fk_push_delivery_subscription FOREIGN KEY (subscription_id) REFERENCES browser_push_subscription(id) ON DELETE CASCADE
);
CREATE INDEX ix_push_due ON browser_push_delivery(next_attempt_at,id);
CREATE INDEX ix_push_expiry ON browser_push_delivery(expires_at);
