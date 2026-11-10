CREATE TABLE space_edit_session (
    id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(36) NOT NULL,
    start_request_id VARCHAR(80) NOT NULL,
    recovery_id VARCHAR(36),
    base_design_version BIGINT NOT NULL,
    draft_version BIGINT NOT NULL,
    draft_json VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    lease_until TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    ended_at TIMESTAMP(6),
    commit_request_id VARCHAR(80),
    committed_version BIGINT,
    CONSTRAINT fk_space_edit_house FOREIGN KEY (household_id) REFERENCES household(id) ON DELETE CASCADE,
    CONSTRAINT fk_space_edit_member FOREIGN KEY (member_id) REFERENCES membership(id) ON DELETE CASCADE,
    CONSTRAINT uq_space_edit_start UNIQUE (member_id,start_request_id)
);
CREATE INDEX ix_space_edit_house_lease ON space_edit_session(household_id,lease_until);
CREATE INDEX ix_space_edit_member_created ON space_edit_session(member_id,created_at);
CREATE INDEX ix_space_edit_expired ON space_edit_session(lease_until);
