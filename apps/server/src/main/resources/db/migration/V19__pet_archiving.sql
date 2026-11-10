ALTER TABLE pet ADD COLUMN archived_at TIMESTAMP(6);
CREATE INDEX ix_pet_household_active ON pet(household_id,archived_at,id);
