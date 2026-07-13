ALTER TABLE generation_record
    ADD COLUMN status VARCHAR(32) NULL;

ALTER TABLE generated_file
    ADD COLUMN file_content CLOB NULL;

ALTER TABLE app_version
    ADD COLUMN preview_url VARCHAR(1024) NULL;

ALTER TABLE app_version
    ADD COLUMN preview_status VARCHAR(32) NULL;

ALTER TABLE app_version
    ADD COLUMN build_status VARCHAR(32) NULL;

ALTER TABLE app_version
    ADD COLUMN build_log CLOB NULL;

ALTER TABLE app_version
    ADD COLUMN built_at TIMESTAMP NULL;
