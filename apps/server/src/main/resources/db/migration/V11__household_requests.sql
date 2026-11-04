CREATE TABLE household_request (
 id VARCHAR(36) PRIMARY KEY,
 household_id VARCHAR(36) NOT NULL,
 author_member_id VARCHAR(36) NOT NULL,
 kind VARCHAR(16) NOT NULL,
 title VARCHAR(80) NOT NULL,
 body VARCHAR(300) NOT NULL,
 targets_json TEXT NOT NULL,
 assignee_member_id VARCHAR(36),
 state VARCHAR(16) NOT NULL,
 due_at TIMESTAMP(6) NOT NULL,
 completed_by_member_id VARCHAR(36),
 completed_at TIMESTAMP(6),
 created_at TIMESTAMP(6) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 version BIGINT NOT NULL,
 CONSTRAINT fk_request_household FOREIGN KEY (household_id) REFERENCES household(id),
 CONSTRAINT fk_request_author FOREIGN KEY (author_member_id) REFERENCES membership(id),
 CONSTRAINT fk_request_assignee FOREIGN KEY (assignee_member_id) REFERENCES membership(id),
 CONSTRAINT fk_request_completer FOREIGN KEY (completed_by_member_id) REFERENCES membership(id)
);
CREATE INDEX ix_request_household_order ON household_request(household_id,created_at,id);

CREATE TABLE request_transition (
 id VARCHAR(36) PRIMARY KEY,
 request_id VARCHAR(36) NOT NULL,
 actor_member_id VARCHAR(36) NOT NULL,
 previous_state VARCHAR(16),
 next_state VARCHAR(16) NOT NULL,
 reason VARCHAR(200),
 occurred_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_transition_request FOREIGN KEY (request_id) REFERENCES household_request(id),
 CONSTRAINT fk_transition_actor FOREIGN KEY (actor_member_id) REFERENCES membership(id)
);

CREATE TABLE request_command (
 id VARCHAR(36) PRIMARY KEY,
 member_id VARCHAR(36) NOT NULL,
 command_scope VARCHAR(64) NOT NULL,
 request_key VARCHAR(80) NOT NULL,
 body_hash VARCHAR(64) NOT NULL,
 response_json TEXT NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_request_command_member FOREIGN KEY (member_id) REFERENCES membership(id),
 CONSTRAINT uq_request_command UNIQUE(member_id,command_scope,request_key)
);
CREATE INDEX ix_request_command_expiry ON request_command(expires_at);
