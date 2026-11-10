ALTER TABLE household_request ADD COLUMN title_editor_member_id VARCHAR(36);
ALTER TABLE household_request ADD COLUMN body_editor_member_id VARCHAR(36);
UPDATE household_request
SET title_editor_member_id=author_member_id,body_editor_member_id=author_member_id
WHERE version=0 AND care_rule_id IS NULL;
CREATE INDEX ix_request_author_history ON household_request(author_member_id,created_at,id);
