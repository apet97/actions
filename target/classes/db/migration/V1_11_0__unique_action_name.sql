-- Enforce unique action names per workspace.
-- Application-level duplicate detection (import) already prevents this,
-- but the DB should enforce it as the source of truth.
CREATE UNIQUE INDEX uq_actions_workspace_name ON actions(workspace_id, name);
