-- Prompt 模板版本追溯字段（nullable，兼容历史数据）
ALTER TABLE model_call_log
  ADD COLUMN prompt_template_version_id BIGINT NULL COMMENT 'Prompt 模板版本 ID' AFTER generation_source,
  ADD COLUMN prompt_template_code VARCHAR(128) NULL COMMENT 'Prompt 模板编码' AFTER prompt_template_version_id,
  ADD COLUMN prompt_template_version_no INT NULL COMMENT 'Prompt 模板版本号' AFTER prompt_template_code;

CREATE INDEX idx_model_call_log_prompt_version ON model_call_log (prompt_template_version_id);
