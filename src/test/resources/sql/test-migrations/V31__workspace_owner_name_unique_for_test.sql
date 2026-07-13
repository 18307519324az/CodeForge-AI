CREATE UNIQUE INDEX IF NOT EXISTS uk_workspace_owner_name ON workspace (owner_user_id, name);
