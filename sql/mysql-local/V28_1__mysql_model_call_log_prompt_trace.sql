-- MySQL-only prompt trace columns (local canonical DB catch-up)
SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'model_call_log'
              AND COLUMN_NAME = 'prompt_template_version_id'
        ),
        'SELECT 1',
        'ALTER TABLE model_call_log ADD COLUMN prompt_template_version_id BIGINT NULL'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'model_call_log'
              AND COLUMN_NAME = 'prompt_template_code'
        ),
        'SELECT 1',
        'ALTER TABLE model_call_log ADD COLUMN prompt_template_code VARCHAR(128) NULL'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'model_call_log'
              AND COLUMN_NAME = 'prompt_template_version_no'
        ),
        'SELECT 1',
        'ALTER TABLE model_call_log ADD COLUMN prompt_template_version_no INT NULL'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'model_call_log'
              AND INDEX_NAME = 'idx_model_call_log_prompt_version'
        ),
        'SELECT 1',
        'CREATE INDEX idx_model_call_log_prompt_version ON model_call_log (prompt_template_version_id)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
