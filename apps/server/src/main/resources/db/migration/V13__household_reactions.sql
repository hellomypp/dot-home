CREATE TABLE household_reaction (
 id VARCHAR(36) PRIMARY KEY,
 household_id VARCHAR(36) NOT NULL,
 author_member_id VARCHAR(36) NOT NULL,
 source_type VARCHAR(16) NOT NULL,
 source_id VARCHAR(128) NOT NULL,
 reaction_code VARCHAR(20) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_reaction_household FOREIGN KEY (household_id) REFERENCES household(id),
 CONSTRAINT fk_reaction_member FOREIGN KEY (author_member_id) REFERENCES membership(id),
 CONSTRAINT uq_household_reaction UNIQUE(household_id,source_type,source_id,author_member_id,reaction_code)
);
