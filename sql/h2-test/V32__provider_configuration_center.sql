UPDATE model_provider
SET is_deleted = 1,
    status     = 'DISABLED',
    updated_at = CURRENT_TIMESTAMP
WHERE provider_code = 'auto'
  AND is_deleted = 0;

ALTER TABLE model_provider
    ADD COLUMN IF NOT EXISTS credential_source VARCHAR(32) NOT NULL DEFAULT 'ENV';

CREATE TABLE IF NOT EXISTS model_provider_credential (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    provider_id      BIGINT       NOT NULL COMMENT '供应商ID',
    credential_type  VARCHAR(32)  NOT NULL DEFAULT 'API_KEY' COMMENT '凭据类型',
    ciphertext       BLOB         NOT NULL COMMENT '密文',
    nonce            BLOB         NOT NULL COMMENT 'GCM Nonce',
    key_version      INT          NOT NULL DEFAULT 1 COMMENT '主密钥版本',
    masked_hint      VARCHAR(16)  NULL COMMENT '掩码提示',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_model_provider_credential_provider UNIQUE (provider_id),
    CONSTRAINT fk_model_provider_credential_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id)
) COMMENT = '模型供应商加密凭据表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_routing_config (
    id                   BIGINT       PRIMARY KEY COMMENT '主键（固定为1）',
    routing_mode         VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT '路由模式 AUTO/PIN',
    pinned_provider_code VARCHAR(64)  NULL COMMENT 'PIN 模式固定供应商编码',
    updated_by           BIGINT       NULL COMMENT '最后更新人',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除'
) COMMENT = 'AI 路由策略配置表' COLLATE = utf8mb4_unicode_ci;

INSERT INTO ai_routing_config (id, routing_mode, pinned_provider_code, updated_by, is_deleted)
SELECT 1, 'AUTO', NULL, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM ai_routing_config WHERE id = 1);

UPDATE model_provider
SET credential_source = 'NONE'
WHERE provider_code = 'rule'
  AND is_deleted = 0;
