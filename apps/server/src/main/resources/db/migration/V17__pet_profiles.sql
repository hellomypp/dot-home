ALTER TABLE pet ADD COLUMN species_group VARCHAR(24);
ALTER TABLE pet ADD COLUMN species_name VARCHAR(80);
ALTER TABLE pet ADD COLUMN guardian_member_id VARCHAR(36);
ALTER TABLE pet ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pet ADD CONSTRAINT fk_pet_guardian FOREIGN KEY (guardian_member_id) REFERENCES membership(id);

UPDATE pet SET species_group = CASE kind WHEN 'dog' THEN 'DOG' WHEN 'cat' THEN 'CAT' ELSE 'OTHER' END;
UPDATE pet SET guardian_member_id = (
    SELECT m.id FROM membership m JOIN household h ON h.id = m.household_id AND h.owner_id = m.user_id
    WHERE m.household_id = pet.household_id AND m.active = TRUE
);
