ALTER TABLE model_provider
    ADD COLUMN IF NOT EXISTS api_protocol VARCHAR(64) NULL;

ALTER TABLE model_provider
    ADD COLUMN IF NOT EXISTS api_key_env VARCHAR(128) NULL;

ALTER TABLE model_provider
    ADD COLUMN IF NOT EXISTS default_model VARCHAR(128) NULL;

ALTER TABLE model_provider
    ADD COLUMN IF NOT EXISTS priority INT NOT NULL DEFAULT 100;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS app_id BIGINT NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS session_id BIGINT NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS provider_code VARCHAR(64) NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS api_protocol VARCHAR(64) NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS fallback_used TINYINT NOT NULL DEFAULT 0;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS generation_source VARCHAR(32) NULL;

ALTER TABLE model_call_log
    ADD COLUMN IF NOT EXISTS created_by BIGINT NULL;
