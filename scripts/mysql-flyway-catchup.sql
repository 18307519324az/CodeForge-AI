-- Manual MySQL flyway history catch-up for legacy databases.
-- Run ONLY after reviewing current flyway_schema_history and backing up MySQL.
-- Usage: mysql --defaults-extra-file=<cnf> codeforge_ai < scripts/mysql-flyway-catchup.sql

DELETE FROM flyway_schema_history WHERE version = '5' AND success = 0;

INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
)
SELECT
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history AS fsh),
    '5',
    'extend model generation tracking',
    'SQL',
    'V5__extend_model_generation_tracking.sql',
    NULL,
    'runtime-recovery',
    NOW(),
    0,
    1
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '5' AND success = 1
);

INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
)
SELECT
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history AS fsh),
    '6',
    'create generation message',
    'SQL',
    'V6__create_generation_message.sql',
    NULL,
    'runtime-recovery',
    NOW(),
    0,
    1
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '6' AND success = 1
);

INSERT INTO flyway_schema_history (
    installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success
)
SELECT
    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history AS fsh),
    '27',
    'model call log prompt trace',
    'SQL',
    'V27__model_call_log_prompt_trace.sql',
    NULL,
    'runtime-recovery',
    NOW(),
    0,
    1
WHERE NOT EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '27' AND success = 1
);
