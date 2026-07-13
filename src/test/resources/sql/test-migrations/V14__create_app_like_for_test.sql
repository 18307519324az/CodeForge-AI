CREATE TABLE IF NOT EXISTS app_like (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id      BIGINT NOT NULL,
    user_id     BIGINT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_like UNIQUE (app_id, user_id),
    CONSTRAINT fk_app_like_app_id FOREIGN KEY (app_id) REFERENCES ai_app (id),
    CONSTRAINT fk_app_like_user_id FOREIGN KEY (user_id) REFERENCES user (id)
);
