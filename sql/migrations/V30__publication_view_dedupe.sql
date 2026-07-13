-- V30__publication_view_dedupe.sql
-- Marketplace view analytics: 24h dedupe per viewer per publication

CREATE TABLE IF NOT EXISTS publication_view_dedupe (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    publication_id      BIGINT NOT NULL COMMENT '发布记录ID',
    viewer_key_hash     VARCHAR(64) NOT NULL COMMENT '浏览者身份哈希',
    last_counted_at     DATETIME(3) NOT NULL COMMENT '上次计浏览时间',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    CONSTRAINT uk_publication_view_dedupe_pub_hash UNIQUE (publication_id, viewer_key_hash),
    INDEX idx_publication_view_dedupe_pub (publication_id),
    INDEX idx_publication_view_dedupe_last_counted (last_counted_at),
    CONSTRAINT fk_publication_view_dedupe_publication_id FOREIGN KEY (publication_id) REFERENCES app_publication (id)
) COMMENT='公开应用浏览去重记录' COLLATE = utf8mb4_unicode_ci;
