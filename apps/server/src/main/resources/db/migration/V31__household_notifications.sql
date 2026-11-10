CREATE TABLE household_notification (
    id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    recipient_member_id VARCHAR(36) NOT NULL,
    actor_member_id VARCHAR(36) NOT NULL,
    source_id VARCHAR(36) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    read_at TIMESTAMP(6),
    CONSTRAINT fk_notification_house FOREIGN KEY (household_id) REFERENCES household(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_member_id) REFERENCES membership(id) ON DELETE CASCADE,
    CONSTRAINT uq_notification_source UNIQUE (recipient_member_id,kind,source_id)
);
CREATE INDEX ix_notification_inbox ON household_notification(recipient_member_id,created_at,id);
CREATE INDEX ix_notification_unread ON household_notification(recipient_member_id,read_at,created_at);
CREATE INDEX ix_notification_retention ON household_notification(created_at);
