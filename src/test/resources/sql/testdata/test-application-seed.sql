ALTER TABLE generation_task
    ADD COLUMN IF NOT EXISTS requirement TEXT;

MERGE INTO user (
    id, account, password_hash, display_name, email, status,
    created_by, updated_by, created_at, updated_at, is_deleted
) KEY(id) VALUES (
    1, 'admin', '$2a$10$WZx9Gra4Q8EDRUtR2WCR/.PsGM824Qy4ZlFdoCDNOdoAxXTK3Y6he', 'System Admin', 'admin@codeforge.ai', 'ACTIVE',
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

MERGE INTO user_role (
    id, user_id, role_code, created_by, updated_by, created_at, updated_at, is_deleted
) KEY(id) VALUES (
    1, 1, 'PLATFORM_ADMIN', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

MERGE INTO workspace (
    id, name, description, owner_user_id, status, plan_code,
    created_by, updated_by, created_at, updated_at, is_deleted
) KEY(id) VALUES (
    1, 'Default Workspace', 'Integration test workspace', 1, 'ACTIVE', 'FREE',
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

MERGE INTO ai_app (
    id, workspace_id, name, description, cover_url, app_type, status, visibility,
    current_version_id, latest_task_id,
    created_by, updated_by, created_at, updated_at, is_deleted
) KEY(id) VALUES (
    1, 1, 'Seeded Demo App', 'Integration test app', NULL, 'WEB_APP', 'DRAFT', 'PRIVATE',
    NULL, NULL,
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

MERGE INTO generation_task (
    id, workspace_id, app_id, task_type, requirement, task_status, idempotency_key,
    retry_of_task_id, retry_count, next_retry_at, request_payload_json, result_summary_json,
    request_id, error_code, error_message, queued_at, started_at, finished_at,
    created_by, updated_by, created_at, updated_at, is_deleted
) KEY(id) VALUES (
    1, 1, 1, 'FULL_GENERATE', 'Generate a seeded integration-test app.', 'SUCCESS', NULL,
    NULL, 0, NULL, NULL, NULL,
    'req-seeded-001', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
);

UPDATE ai_app
SET latest_task_id = 1,
    updated_by = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;
