ALTER TABLE household ADD COLUMN preset_id VARCHAR(32) NOT NULL DEFAULT 'classic_home';
ALTER TABLE household ADD COLUMN design_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE household ADD COLUMN object_variants VARCHAR(512) NOT NULL DEFAULT '{}';
-- Preserve legacy coordinates. World projects them to a valid spawn until the next home move.
ALTER TABLE membership ADD COLUMN avatar_space VARCHAR(16) NOT NULL DEFAULT 'legacy';
