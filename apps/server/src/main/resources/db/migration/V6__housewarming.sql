CREATE TABLE housewarming_invitation (
 id VARCHAR(36) PRIMARY KEY, household_id VARCHAR(36) NOT NULL, created_by_member_id VARCHAR(36) NOT NULL,
 token_hash VARCHAR(64) NOT NULL UNIQUE, created_at TIMESTAMP(6) NOT NULL, expires_at TIMESTAMP(6) NOT NULL, revoked_at TIMESTAMP(6),
 CONSTRAINT fk_visit_house FOREIGN KEY (household_id) REFERENCES household(id),
 CONSTRAINT fk_visit_author FOREIGN KEY (created_by_member_id) REFERENCES membership(id)
);
CREATE INDEX ix_visit_house ON housewarming_invitation(household_id,created_at);
CREATE TABLE guest_access (
 id VARCHAR(36) PRIMARY KEY, invitation_id VARCHAR(36) NOT NULL, visitor_account_id VARCHAR(36) NOT NULL, visited_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT uq_guest_access UNIQUE(invitation_id,visitor_account_id),
 CONSTRAINT fk_access_invite FOREIGN KEY(invitation_id) REFERENCES housewarming_invitation(id),
 CONSTRAINT fk_access_account FOREIGN KEY(visitor_account_id) REFERENCES app_user(id)
);
CREATE TABLE guestbook_entry (
 id VARCHAR(36) PRIMARY KEY, household_id VARCHAR(36) NOT NULL, visitor_account_id VARCHAR(36) NOT NULL,
 request_id VARCHAR(80) NOT NULL, message VARCHAR(1000) NOT NULL, created_at TIMESTAMP(6) NOT NULL, hidden_at TIMESTAMP(6),
 CONSTRAINT uq_guestbook_request UNIQUE(household_id,visitor_account_id,request_id),
 CONSTRAINT fk_guestbook_house FOREIGN KEY(household_id) REFERENCES household(id),
 CONSTRAINT fk_guestbook_account FOREIGN KEY(visitor_account_id) REFERENCES app_user(id)
);
CREATE INDEX ix_guestbook_house ON guestbook_entry(household_id,created_at);
