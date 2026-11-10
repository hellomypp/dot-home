CREATE TABLE household_export (
    id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    export_state VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    payload LONGTEXT,
    CONSTRAINT fk_export_house FOREIGN KEY (household_id) REFERENCES household(id),
    CONSTRAINT fk_export_member FOREIGN KEY (member_id) REFERENCES membership(id),
    CONSTRAINT uq_export_request UNIQUE (member_id,request_id)
);
CREATE INDEX ix_export_pending ON household_export(export_state,created_at);
CREATE INDEX ix_export_expiry ON household_export(expires_at);
