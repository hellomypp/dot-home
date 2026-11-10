CREATE TABLE household_notification_preference (
    member_id VARCHAR(36) PRIMARY KEY,
    allowed_mask INTEGER NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_notification_preference_member FOREIGN KEY (member_id) REFERENCES membership(id) ON DELETE CASCADE
);
