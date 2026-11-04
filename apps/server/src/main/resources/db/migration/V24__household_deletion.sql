ALTER TABLE household ADD COLUMN pending_delete_until TIMESTAMP(6);
ALTER TABLE household ADD COLUMN deletion_request_id VARCHAR(80);
ALTER TABLE household ADD COLUMN deletion_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX ix_household_pending_delete ON household(pending_delete_until,id);

CREATE TABLE household_deletion_log (
    household_id VARCHAR(36) PRIMARY KEY,
    deleted_at TIMESTAMP(6) NOT NULL
);
