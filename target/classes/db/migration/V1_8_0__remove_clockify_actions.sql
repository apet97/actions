-- Remove unused Clockify Actions columns (dead code cleanup).
-- These columns were added in V1_7_0 for a pre-built Clockify operations feature
-- that was never exposed in the UI. All actions are type CUSTOM.

ALTER TABLE actions DROP COLUMN IF EXISTS action_type;
ALTER TABLE actions DROP COLUMN IF EXISTS operation_id;
ALTER TABLE actions DROP COLUMN IF EXISTS parameters_enc;
