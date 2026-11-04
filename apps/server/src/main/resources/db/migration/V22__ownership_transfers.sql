CREATE TABLE ownership_transfer (
    id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    from_member_id VARCHAR(36) NOT NULL,
    target_member_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    transfer_state VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    responded_at TIMESTAMP(6),
    version BIGINT NOT NULL,
    CONSTRAINT fk_transfer_household FOREIGN KEY (household_id) REFERENCES household(id),
    CONSTRAINT fk_transfer_from FOREIGN KEY (from_member_id) REFERENCES membership(id),
    CONSTRAINT fk_transfer_target FOREIGN KEY (target_member_id) REFERENCES membership(id),
    CONSTRAINT uq_transfer_request UNIQUE (from_member_id,request_id)
);
CREATE INDEX ix_transfer_house_pending ON ownership_transfer(household_id,transfer_state,created_at);
