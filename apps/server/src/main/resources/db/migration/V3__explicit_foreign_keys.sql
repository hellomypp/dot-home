-- MySQL requires table-level FOREIGN KEY constraints; column-level REFERENCES
-- in V1 is enforced by H2 but not by every MySQL version.
ALTER TABLE household ADD CONSTRAINT fk_house_owner FOREIGN KEY (owner_id) REFERENCES app_user(id);
ALTER TABLE membership ADD CONSTRAINT fk_member_house FOREIGN KEY (household_id) REFERENCES household(id);
ALTER TABLE membership ADD CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES app_user(id);
ALTER TABLE pet ADD CONSTRAINT fk_pet_house FOREIGN KEY (household_id) REFERENCES household(id);
ALTER TABLE invitation ADD CONSTRAINT fk_invite_house FOREIGN KEY (household_id) REFERENCES household(id);
ALTER TABLE invitation ADD CONSTRAINT fk_invite_user FOREIGN KEY (accepted_by) REFERENCES app_user(id);
ALTER TABLE pantry_item ADD CONSTRAINT fk_pantry_house FOREIGN KEY (household_id) REFERENCES household(id);
ALTER TABLE pantry_change ADD CONSTRAINT fk_change_house FOREIGN KEY (household_id) REFERENCES household(id);
ALTER TABLE pantry_change ADD CONSTRAINT fk_change_item FOREIGN KEY (item_id) REFERENCES pantry_item(id);
ALTER TABLE pantry_change ADD CONSTRAINT fk_change_member FOREIGN KEY (author_member_id) REFERENCES membership(id);
ALTER TABLE life_event ADD CONSTRAINT fk_event_house FOREIGN KEY (household_id) REFERENCES household(id);
ALTER TABLE life_event ADD CONSTRAINT fk_event_member FOREIGN KEY (author_member_id) REFERENCES membership(id);
