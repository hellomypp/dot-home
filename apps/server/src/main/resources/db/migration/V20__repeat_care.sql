CREATE TABLE repeat_care_rule (
    id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    profile_type VARCHAR(8) NOT NULL,
    profile_id VARCHAR(36) NOT NULL,
    title VARCHAR(80) NOT NULL,
    weekday_mask INT NOT NULL,
    local_time TIME NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    next_at TIMESTAMP(6) NOT NULL,
    ended_at TIMESTAMP(6),
    version BIGINT NOT NULL,
    CONSTRAINT fk_care_rule_household FOREIGN KEY (household_id) REFERENCES household(id)
);
CREATE INDEX ix_care_rule_due ON repeat_care_rule(ended_at,next_at,id);
CREATE INDEX ix_care_rule_profile ON repeat_care_rule(household_id,profile_type,profile_id,ended_at);

ALTER TABLE household_request ADD COLUMN care_rule_id VARCHAR(36);
ALTER TABLE household_request ADD COLUMN care_scheduled_at TIMESTAMP(6);
ALTER TABLE household_request ADD CONSTRAINT fk_request_care_rule FOREIGN KEY (care_rule_id) REFERENCES repeat_care_rule(id);
ALTER TABLE household_request ADD CONSTRAINT uq_request_care_occurrence UNIQUE (care_rule_id,care_scheduled_at);
