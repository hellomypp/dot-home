ALTER TABLE household_calendar_event ADD COLUMN event_scope VARCHAR(24) NOT NULL DEFAULT 'HOUSEHOLD';
ALTER TABLE household_calendar_event ADD COLUMN hidden_at TIMESTAMP(6);
