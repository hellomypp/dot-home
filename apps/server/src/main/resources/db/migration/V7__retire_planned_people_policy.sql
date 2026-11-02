-- Compatibility window: older binaries still map this NOT NULL column.
-- Preserve every existing value; new binaries do not map/read/write it.
-- Drop the column in a later migration after rollback support is retired.
ALTER TABLE household ALTER COLUMN planned_people SET DEFAULT 1;
