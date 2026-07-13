ALTER TABLE generation_task
    ADD COLUMN prompt_template_id BIGINT NULL,
    ADD COLUMN prompt_template_version_id BIGINT NULL;

CREATE INDEX idx_generation_task_prompt_template
    ON generation_task (prompt_template_id);

CREATE INDEX idx_generation_task_prompt_template_version
    ON generation_task (prompt_template_version_id);

ALTER TABLE model_call_log
    ADD COLUMN system_prompt_sha256 VARCHAR(64) NULL,
    ADD COLUMN user_prompt_sha256 VARCHAR(64) NULL,
    ADD COLUMN combined_prompt_fingerprint VARCHAR(64) NULL;
