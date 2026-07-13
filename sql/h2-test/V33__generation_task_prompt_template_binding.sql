-- generation_task: execution-time source of truth for pinned template version
ALTER TABLE generation_task
    ADD COLUMN IF NOT EXISTS prompt_template_id BIGINT NULL;

ALTER TABLE generation_task
    ADD COLUMN IF NOT EXISTS prompt_template_version_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_generation_task_prompt_template
    ON generation_task (prompt_template_id);

CREATE INDEX IF NOT EXISTS idx_generation_task_prompt_template_version
    ON generation_task (prompt_template_version_id);

-- model_call_log: prompt fingerprint trace (no full prompt content)
ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS system_prompt_sha256 VARCHAR(64) NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS user_prompt_sha256 VARCHAR(64) NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS combined_prompt_fingerprint VARCHAR(64) NULL;
