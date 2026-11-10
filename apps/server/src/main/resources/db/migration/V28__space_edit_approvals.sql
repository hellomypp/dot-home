CREATE TABLE space_edit_request (
    id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(80) NOT NULL,
    decision_request_id VARCHAR(80),
    scope VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    start_before TIMESTAMP(6),
    version BIGINT NOT NULL,
    CONSTRAINT fk_space_request_house FOREIGN KEY (household_id) REFERENCES household(id) ON DELETE CASCADE,
    CONSTRAINT fk_space_request_member FOREIGN KEY (member_id) REFERENCES membership(id) ON DELETE CASCADE,
    CONSTRAINT uq_space_request_command UNIQUE (member_id,request_id)
);
CREATE INDEX ix_space_request_house_state ON space_edit_request(household_id,state,created_at);
CREATE INDEX ix_space_request_member_created ON space_edit_request(member_id,created_at);
ALTER TABLE space_edit_session ADD COLUMN approval_id VARCHAR(36);
