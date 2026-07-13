CREATE TABLE IF NOT EXISTS generation_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id    BIGINT NOT NULL,
    app_id          BIGINT NOT NULL,
    task_id         BIGINT NULL,
    user_id         BIGINT NULL,
    message_role    VARCHAR(32) NOT NULL,
    message_content TEXT NOT NULL,
    message_type    VARCHAR(32) NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted      TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_generation_message_workspace_id FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_generation_message_app_id FOREIGN KEY (app_id) REFERENCES ai_app (id),
    CONSTRAINT fk_generation_message_task_id FOREIGN KEY (task_id) REFERENCES generation_task (id),
    CONSTRAINT fk_generation_message_user_id FOREIGN KEY (user_id) REFERENCES user (id)
);

CREATE INDEX IF NOT EXISTS idx_generation_message_app_created_at
    ON generation_message (app_id, created_at);

CREATE INDEX IF NOT EXISTS idx_generation_message_task_created_at
    ON generation_message (task_id, created_at);
