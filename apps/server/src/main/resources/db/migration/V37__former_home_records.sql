CREATE TABLE former_record_deletion (
    record_kind VARCHAR(20) NOT NULL,
    record_id VARCHAR(36) NOT NULL,
    household_id VARCHAR(36) NOT NULL,
    author_member_id VARCHAR(36) NOT NULL,
    deleted_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (record_kind,record_id)
);
CREATE INDEX ix_object_note_author_history ON object_note(author_member_id,created_at,id);
