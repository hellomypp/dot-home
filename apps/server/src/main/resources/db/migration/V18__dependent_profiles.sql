CREATE TABLE dependent_profile (
    id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    profile_type VARCHAR(16) NOT NULL,
    name VARCHAR(40) NOT NULL,
    guardian_member_id VARCHAR(36) NOT NULL,
    appearance_profile VARCHAR(24) NOT NULL,
    archived_at TIMESTAMP(6),
    version BIGINT NOT NULL,
    CONSTRAINT fk_dependent_household FOREIGN KEY (household_id) REFERENCES household(id),
    CONSTRAINT fk_dependent_guardian FOREIGN KEY (guardian_member_id) REFERENCES membership(id)
);
CREATE INDEX ix_dependent_household_active ON dependent_profile(household_id,archived_at,id);
