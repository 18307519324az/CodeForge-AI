-- Prompt 模板版本追溯字段（nullable，兼容历史数据）
ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS prompt_template_version_id BIGINT NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS prompt_template_code VARCHAR(128) NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS prompt_template_version_no INT NULL;

CREATE INDEX IF NOT EXISTS idx_model_call_log_prompt_version ON model_call_log (prompt_template_version_id);
