ALTER TABLE household_calendar_event ADD COLUMN target_ids TEXT;
ALTER TABLE household_calendar_event ADD COLUMN reminder_minutes_before INT;

CREATE TABLE calendar_attendance (
 id VARCHAR(36) PRIMARY KEY,
 event_id VARCHAR(36) NOT NULL,
 member_id VARCHAR(36) NOT NULL,
 attendance_status VARCHAR(16) NOT NULL,
 updated_at TIMESTAMP(6) NOT NULL,
 version BIGINT NOT NULL,
 CONSTRAINT fk_calendar_attendance_event FOREIGN KEY (event_id) REFERENCES household_calendar_event(id),
 CONSTRAINT fk_calendar_attendance_member FOREIGN KEY (member_id) REFERENCES membership(id),
 CONSTRAINT uq_calendar_attendance UNIQUE(event_id,member_id)
);
