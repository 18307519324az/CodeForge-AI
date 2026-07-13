UPDATE model_provider
SET is_deleted = 1,
    status     = 'DISABLED',
    updated_at = CURRENT_TIMESTAMP
WHERE provider_code = 'auto'
  AND is_deleted = 0;

ALTER TABLE model_provider
    ADD COLUMN credential_source VARCHAR(32) NOT NULL DEFAULT 'ENV';

CREATE TABLE IF NOT EXISTS model_provider_credential (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id      BIGINT       NOT NULL,
    credential_type  VARCHAR(32)  NOT NULL DEFAULT 'API_KEY',
    ciphertext       BLOB         NOT NULL,
    nonce            BLOB         NOT NULL,
    key_version      INT          NOT NULL DEFAULT 1,
    masked_hint      VARCHAR(16)  NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted       TINYINT      NOT NULL DEFAULT 0,
    UNIQUE KEY uk_model_provider_credential_provider (provider_id),
    CONSTRAINT fk_model_provider_credential_provider FOREIGN KEY (provider_id) REFERENCES model_provider (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_routing_config (
    id                   BIGINT       PRIMARY KEY,
    routing_mode         VARCHAR(16)  NOT NULL DEFAULT 'AUTO',
    pinned_provider_code VARCHAR(64)  NULL,
    updated_by           BIGINT       NULL,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted           TINYINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO ai_routing_config (id, routing_mode, pinned_provider_code, updated_by, is_deleted)
SELECT 1, 'AUTO', NULL, NULL, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_routing_config WHERE id = 1);

UPDATE model_provider
SET credential_source = 'NONE'
WHERE provider_code = 'rule'
  AND is_deleted = 0;
