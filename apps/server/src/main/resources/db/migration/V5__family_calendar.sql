CREATE TABLE household_calendar_event (
 id VARCHAR(36) PRIMARY KEY, household_id VARCHAR(36) NOT NULL,
 title VARCHAR(120) NOT NULL, description VARCHAR(2000) NOT NULL, category VARCHAR(20) NOT NULL,
 all_day BOOLEAN NOT NULL, start_date DATE, end_date DATE, start_at TIMESTAMP(6), end_at TIMESTAMP(6), time_zone VARCHAR(64) NOT NULL,
 created_by_member_id VARCHAR(36) NOT NULL, updated_by_member_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL, updated_at TIMESTAMP(6) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_calendar_household FOREIGN KEY (household_id) REFERENCES household(id),
 CONSTRAINT fk_calendar_creator FOREIGN KEY (created_by_member_id) REFERENCES membership(id),
 CONSTRAINT fk_calendar_editor FOREIGN KEY (updated_by_member_id) REFERENCES membership(id),
 CONSTRAINT ck_calendar_dates CHECK ((all_day=true AND start_date IS NOT NULL AND end_date IS NOT NULL AND end_date>=start_date AND start_at IS NULL AND end_at IS NULL) OR (all_day=false AND start_at IS NOT NULL AND end_at IS NOT NULL AND end_at>=start_at AND start_date IS NULL AND end_date IS NULL))
);
CREATE INDEX ix_calendar_dates ON household_calendar_event(household_id,start_date,end_date);
CREATE INDEX ix_calendar_times ON household_calendar_event(household_id,start_at,end_at);
