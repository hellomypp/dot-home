CREATE TABLE care_photo (
    id VARCHAR(36) PRIMARY KEY,
    household_id VARCHAR(36) NOT NULL,
    guardian_member_id VARCHAR(36) NOT NULL,
    name VARCHAR(40) NOT NULL,
    png_base64 VARCHAR(8192) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_care_photo_house FOREIGN KEY (household_id) REFERENCES household(id),
    CONSTRAINT fk_care_photo_guardian FOREIGN KEY (guardian_member_id) REFERENCES membership(id)
);
CREATE INDEX ix_care_photo_album ON care_photo(household_id,created_at,id);
