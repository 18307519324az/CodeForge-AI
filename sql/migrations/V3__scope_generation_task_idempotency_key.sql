ALTER TABLE generation_task
    DROP INDEX uk_generation_task_idem_key;

ALTER TABLE generation_task
    ADD CONSTRAINT uk_generation_task_scope_idem_key UNIQUE (workspace_id, app_id, idempotency_key);
