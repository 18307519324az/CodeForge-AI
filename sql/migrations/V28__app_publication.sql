-- V28__app_publication.sql
-- Publish MVP: one active publication record per app

CREATE TABLE IF NOT EXISTS app_publication (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    app_id              BIGINT NOT NULL COMMENT '应用ID',
    version_id          BIGINT NOT NULL COMMENT '固定发布版本ID',
    publisher_user_id   BIGINT NOT NULL COMMENT '发布人用户ID',
    public_title        VARCHAR(128) NOT NULL COMMENT '公开标题',
    public_description  VARCHAR(1024) NULL COMMENT '公开简介',
    slug                VARCHAR(128) NOT NULL COMMENT '公开访问标识',
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
    allow_preview       TINYINT NOT NULL DEFAULT 1 COMMENT '允许在线预览',
    allow_download      TINYINT NOT NULL DEFAULT 0 COMMENT '允许源码下载',
    published_at        DATETIME NULL COMMENT '发布时间',
    unpublished_at      DATETIME NULL COMMENT '取消发布时间',
    view_count          BIGINT NOT NULL DEFAULT 0 COMMENT '浏览次数',
    download_count      BIGINT NOT NULL DEFAULT 0 COMMENT '下载次数',
    created_by          BIGINT NULL COMMENT '创建人',
    updated_by          BIGINT NULL COMMENT '更新人',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_app_publication_app_id UNIQUE (app_id),
    CONSTRAINT uk_app_publication_slug UNIQUE (slug),
    CONSTRAINT fk_app_publication_app_id FOREIGN KEY (app_id) REFERENCES ai_app (id),
    CONSTRAINT fk_app_publication_publisher_user_id FOREIGN KEY (publisher_user_id) REFERENCES user (id),
    INDEX idx_app_publication_status_published_at (status, published_at DESC, id DESC),
    INDEX idx_app_publication_publisher_user_id (publisher_user_id),
    INDEX idx_app_publication_version_id (version_id)
) COMMENT='应用公开发布表' COLLATE = utf8mb4_unicode_ci;
