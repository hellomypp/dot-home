CREATE TABLE object_note (
 id VARCHAR(36) PRIMARY KEY,
 household_id VARCHAR(36) NOT NULL,
 object_id VARCHAR(24) NOT NULL,
 author_member_id VARCHAR(36) NOT NULL,
 body VARCHAR(1000) NOT NULL,
 state VARCHAR(16) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 taken_by_member_id VARCHAR(36),
 taken_at TIMESTAMP(6),
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_note_household FOREIGN KEY (household_id) REFERENCES household(id),
 CONSTRAINT fk_note_author FOREIGN KEY (author_member_id) REFERENCES membership(id),
 CONSTRAINT fk_note_reader FOREIGN KEY (taken_by_member_id) REFERENCES membership(id),
 CONSTRAINT ck_note_state CHECK ((state='ATTACHED' AND taken_by_member_id IS NULL AND taken_at IS NULL) OR (state='TAKEN' AND taken_by_member_id IS NOT NULL AND taken_at IS NOT NULL))
);
CREATE INDEX ix_note_object_state ON object_note(household_id,object_id,state,created_at);
