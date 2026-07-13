-- V1__init.sql
-- 首版初始化迁移脚本
-- 约定：
-- 1. 该脚本面向 Flyway 一类迁移工具执行
-- 2. 不在脚本中创建数据库，也不使用 USE
-- 3. 数据源 schema 由外部环境配置保证

-- =========================
-- 1. 身份与权限
-- =========================

CREATE TABLE IF NOT EXISTS user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    account         VARCHAR(128) NOT NULL COMMENT '登录账号',
    password_hash   VARCHAR(255) NOT NULL COMMENT '密码哈希',
    display_name    VARCHAR(128) NULL COMMENT '显示名称',
    avatar_url      VARCHAR(1024) NULL COMMENT '头像地址',
    email           VARCHAR(256) NULL COMMENT '邮箱',
    phone           VARCHAR(64) NULL COMMENT '手机号',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态',
    last_login_at   DATETIME NULL COMMENT '最近登录时间',
    created_by      BIGINT NULL COMMENT '创建人',
    updated_by      BIGINT NULL COMMENT '更新人',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_user_account UNIQUE (account),
    CONSTRAINT uk_user_email UNIQUE (email),
    INDEX idx_user_status (status),
    INDEX idx_user_created_at (created_at)
) COMMENT='用户表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_role (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    role_code       VARCHAR(64) NOT NULL COMMENT '平台角色编码',
    created_by      BIGINT NULL COMMENT '创建人',
    updated_by      BIGINT NULL COMMENT '更新人',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_user_role UNIQUE (user_id, role_code),
    CONSTRAINT fk_user_role_user_id FOREIGN KEY (user_id) REFERENCES user (id),
    INDEX idx_user_role_role_code (role_code)
) COMMENT='用户平台角色表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 2. 工作空间
-- =========================

CREATE TABLE workspace (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name            VARCHAR(128) NOT NULL COMMENT '空间名称',
    description     VARCHAR(512) NULL COMMENT '空间描述',
    owner_user_id   BIGINT NOT NULL COMMENT '拥有者用户ID',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '空间状态',
    plan_code       VARCHAR(64) NOT NULL DEFAULT 'FREE' COMMENT '套餐编码',
    created_by      BIGINT NULL COMMENT '创建人',
    updated_by      BIGINT NULL COMMENT '更新人',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_workspace_owner_user_id FOREIGN KEY (owner_user_id) REFERENCES user (id),
    INDEX idx_workspace_owner_user_id (owner_user_id),
    INDEX idx_workspace_status (status)
) COMMENT='工作空间表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE workspace_member (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workspace_id    BIGINT NOT NULL COMMENT '工作空间ID',
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    member_role     VARCHAR(32) NOT NULL COMMENT '空间成员角色',
    member_status   VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '成员状态',
    invited_by      BIGINT NULL COMMENT '邀请人ID',
    joined_at       DATETIME NULL COMMENT '加入时间',
    created_by      BIGINT NULL COMMENT '创建人',
    updated_by      BIGINT NULL COMMENT '更新人',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_workspace_member UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_workspace_member_workspace_id FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_workspace_member_user_id FOREIGN KEY (user_id) REFERENCES user (id),
    INDEX idx_workspace_member_role (member_role),
    INDEX idx_workspace_member_status (member_status)
) COMMENT='工作空间成员表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 3. 应用中心
-- =========================

CREATE TABLE ai_app (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workspace_id        BIGINT NOT NULL COMMENT '工作空间ID',
    name                VARCHAR(128) NOT NULL COMMENT '应用名称',
    description         VARCHAR(512) NULL COMMENT '应用描述',
    cover_url           VARCHAR(1024) NULL COMMENT '封面地址',
    app_type            VARCHAR(64) NOT NULL COMMENT '应用类型',
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '应用状态',
    visibility          VARCHAR(32) NOT NULL DEFAULT 'PRIVATE' COMMENT '可见性',
    current_version_id  BIGINT NULL COMMENT '当前版本ID',
    latest_task_id      BIGINT NULL COMMENT '最近任务ID',
    created_by          BIGINT NOT NULL COMMENT '创建人',
    updated_by          BIGINT NULL COMMENT '更新人',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_ai_app_workspace_id FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_ai_app_created_by FOREIGN KEY (created_by) REFERENCES user (id),
    INDEX idx_ai_app_workspace_status (workspace_id, status, updated_at),
    INDEX idx_ai_app_created_by (created_by),
    INDEX idx_ai_app_visibility (visibility)
) COMMENT='AI应用表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 4. 提示词模板
-- =========================

CREATE TABLE prompt_template (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workspace_id        BIGINT NOT NULL COMMENT '工作空间ID',
    template_name       VARCHAR(128) NOT NULL COMMENT '模板名称',
    template_scene      VARCHAR(64) NOT NULL COMMENT '模板场景',
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '模板状态',
    current_version_no  INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
    remark              VARCHAR(512) NULL COMMENT '备注',
    created_by          BIGINT NOT NULL COMMENT '创建人',
    updated_by          BIGINT NULL COMMENT '更新人',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_prompt_template_workspace_id FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_prompt_template_created_by FOREIGN KEY (created_by) REFERENCES user (id),
    CONSTRAINT uk_prompt_template_name UNIQUE (workspace_id, template_name),
    INDEX idx_prompt_template_scene_status (template_scene, status)
) COMMENT='提示词模板表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE prompt_template_version (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_id         BIGINT NOT NULL COMMENT '模板ID',
    version_no          INT NOT NULL COMMENT '版本号',
    system_prompt       MEDIUMTEXT NOT NULL COMMENT '系统提示词',
    user_prompt         MEDIUMTEXT NOT NULL COMMENT '用户提示词',
    variables_json      JSON NULL COMMENT '变量定义',
    model_strategy_json JSON NULL COMMENT '模型策略定义',
    published_by        BIGINT NULL COMMENT '发布人',
    published_at        DATETIME NULL COMMENT '发布时间',
    created_by          BIGINT NOT NULL COMMENT '创建人',
    updated_by          BIGINT NULL COMMENT '更新人',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_prompt_template_version_template_id FOREIGN KEY (template_id) REFERENCES prompt_template (id),
    CONSTRAINT uk_prompt_template_version UNIQUE (template_id, version_no),
    INDEX idx_prompt_template_version_published_at (published_at)
) COMMENT='提示词模板版本表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 5. 模型治理
-- =========================

CREATE TABLE model_provider (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    provider_code   VARCHAR(64) NOT NULL COMMENT '供应商编码',
    provider_name   VARCHAR(128) NOT NULL COMMENT '供应商名称',
    base_url        VARCHAR(1024) NULL COMMENT '接口基础地址',
    auth_mode       VARCHAR(32) NOT NULL DEFAULT 'API_KEY' COMMENT '认证模式',
    secret_ref      VARCHAR(256) NULL COMMENT '密钥引用',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_by      BIGINT NULL COMMENT '创建人',
    updated_by      BIGINT NULL COMMENT '更新人',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_model_provider_code UNIQUE (provider_code),
    INDEX idx_model_provider_status (status)
) COMMENT='模型供应商表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 6. 生成任务与记录
-- =========================

CREATE TABLE generation_task (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workspace_id            BIGINT NOT NULL COMMENT '工作空间ID',
    app_id                  BIGINT NOT NULL COMMENT '应用ID',
    task_type               VARCHAR(64) NOT NULL COMMENT '任务类型',
    task_status             VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT '任务状态',
    idempotency_key         VARCHAR(128) NULL COMMENT '幂等键',
    retry_of_task_id        BIGINT NULL COMMENT '重试来源任务ID',
    retry_count             INT NOT NULL DEFAULT 0 COMMENT '当前重试次数',
    next_retry_at           DATETIME NULL COMMENT '下次重试时间',
    request_payload_json    JSON NULL COMMENT '请求载荷',
    result_summary_json     JSON NULL COMMENT '结果摘要',
    request_id              VARCHAR(128) NULL COMMENT '请求标识',
    error_code              VARCHAR(64) NULL COMMENT '错误码',
    error_message           VARCHAR(1024) NULL COMMENT '错误信息',
    queued_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    started_at              DATETIME NULL COMMENT '开始时间',
    finished_at             DATETIME NULL COMMENT '完成时间',
    created_by              BIGINT NOT NULL COMMENT '创建人',
    updated_by              BIGINT NULL COMMENT '更新人',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted              TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_generation_task_workspace_id FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_generation_task_app_id FOREIGN KEY (app_id) REFERENCES ai_app (id),
    CONSTRAINT fk_generation_task_created_by FOREIGN KEY (created_by) REFERENCES user (id),
    CONSTRAINT fk_generation_task_retry_of_task_id FOREIGN KEY (retry_of_task_id) REFERENCES generation_task (id),
    CONSTRAINT uk_generation_task_idem_key UNIQUE (idempotency_key),
    INDEX idx_generation_task_app_status (app_id, task_status, created_at),
    INDEX idx_generation_task_workspace_status (workspace_id, task_status, created_at),
    INDEX idx_generation_task_request_id (request_id),
    INDEX idx_generation_task_retry_next (task_status, next_retry_at)
) COMMENT='生成任务表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE generation_task_event (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_id             BIGINT NOT NULL COMMENT '任务ID',
    event_type          VARCHAR(64) NOT NULL COMMENT '事件类型',
    event_message       VARCHAR(1024) NULL COMMENT '事件消息',
    event_payload_json  JSON NULL COMMENT '事件载荷',
    created_by          BIGINT NULL COMMENT '创建人',
    updated_by          BIGINT NULL COMMENT '更新人',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_generation_task_event_task_id FOREIGN KEY (task_id) REFERENCES generation_task (id),
    INDEX idx_generation_task_event_task_created (task_id, created_at),
    INDEX idx_generation_task_event_type (event_type)
) COMMENT='生成任务事件表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE generation_record (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workspace_id                    BIGINT NOT NULL COMMENT '工作空间ID',
    app_id                          BIGINT NOT NULL COMMENT '应用ID',
    task_id                         BIGINT NOT NULL COMMENT '任务ID',
    prompt_template_version_id      BIGINT NULL COMMENT '提示词模板版本ID',
    model_provider_id               BIGINT NULL COMMENT '模型供应商ID',
    model_name                      VARCHAR(128) NULL COMMENT '模型名称',
    input_summary                   TEXT NULL COMMENT '输入摘要',
    output_summary                  TEXT NULL COMMENT '输出摘要',
    token_input                     INT NOT NULL DEFAULT 0 COMMENT '输入Token',
    token_output                    INT NOT NULL DEFAULT 0 COMMENT '输出Token',
    duration_ms                     BIGINT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    created_by                      BIGINT NOT NULL COMMENT '创建人',
    updated_by                      BIGINT NULL COMMENT '更新人',
    created_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at                      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted                      TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_generation_record_workspace_id FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_generation_record_app_id FOREIGN KEY (app_id) REFERENCES ai_app (id),
    CONSTRAINT fk_generation_record_task_id FOREIGN KEY (task_id) REFERENCES generation_task (id),
    CONSTRAINT fk_generation_record_prompt_template_version_id FOREIGN KEY (prompt_template_version_id) REFERENCES prompt_template_version (id),
    CONSTRAINT fk_generation_record_model_provider_id FOREIGN KEY (model_provider_id) REFERENCES model_provider (id),
    INDEX idx_generation_record_app_created_at (app_id, created_at),
    INDEX idx_generation_record_task_id (task_id)
) COMMENT='生成记录表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE model_call_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    task_id         BIGINT NOT NULL COMMENT '任务ID',
    provider_id     BIGINT NOT NULL COMMENT '供应商ID',
    model_name      VARCHAR(128) NOT NULL COMMENT '模型名称',
    request_id      VARCHAR(128) NULL COMMENT '请求标识',
    status          VARCHAR(32) NOT NULL COMMENT '调用状态',
    input_tokens    INT NOT NULL DEFAULT 0 COMMENT '输入Token',
    output_tokens   INT NOT NULL DEFAULT 0 COMMENT '输出Token',
    duration_ms     BIGINT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    error_message   VARCHAR(1024) NULL COMMENT '错误信息',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_model_call_log_task_id FOREIGN KEY (task_id) REFERENCES generation_task (id),
    CONSTRAINT fk_model_call_log_provider_id FOREIGN KEY (provider_id) REFERENCES model_provider (id),
    INDEX idx_model_call_log_task_provider (task_id, provider_id),
    INDEX idx_model_call_log_request_id (request_id),
    INDEX idx_model_call_log_created_at (created_at)
) COMMENT='模型调用日志表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 7. 版本与文件
-- =========================

CREATE TABLE app_version (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    app_id              BIGINT NOT NULL COMMENT '应用ID',
    version_no          INT NOT NULL COMMENT '版本号',
    version_source      VARCHAR(64) NOT NULL COMMENT '版本来源',
    source_task_id      BIGINT NULL COMMENT '来源任务ID',
    change_summary      VARCHAR(1024) NULL COMMENT '变更摘要',
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '版本状态',
    published_at        DATETIME NULL COMMENT '发布时间',
    created_by          BIGINT NOT NULL COMMENT '创建人',
    updated_by          BIGINT NULL COMMENT '更新人',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_app_version_app_id FOREIGN KEY (app_id) REFERENCES ai_app (id),
    CONSTRAINT fk_app_version_source_task_id FOREIGN KEY (source_task_id) REFERENCES generation_task (id),
    CONSTRAINT uk_app_version UNIQUE (app_id, version_no),
    INDEX idx_app_version_app_version_no (app_id, version_no),
    INDEX idx_app_version_created_at (created_at)
) COMMENT='应用版本表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE artifact_snapshot (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    app_version_id          BIGINT NOT NULL COMMENT '应用版本ID',
    snapshot_type           VARCHAR(64) NOT NULL COMMENT '快照类型',
    snapshot_path           VARCHAR(1024) NULL COMMENT '快照路径',
    snapshot_content_json   JSON NULL COMMENT '快照内容',
    content_hash            VARCHAR(128) NULL COMMENT '内容哈希',
    created_by              BIGINT NOT NULL COMMENT '创建人',
    updated_by              BIGINT NULL COMMENT '更新人',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted              TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_artifact_snapshot_app_version_id FOREIGN KEY (app_version_id) REFERENCES app_version (id),
    INDEX idx_artifact_snapshot_version_type (app_version_id, snapshot_type)
) COMMENT='制品快照表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE generated_file (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    app_version_id      BIGINT NOT NULL COMMENT '应用版本ID',
    file_path           VARCHAR(1024) NOT NULL COMMENT '文件路径',
    file_name           VARCHAR(256) NOT NULL COMMENT '文件名',
    file_type           VARCHAR(64) NOT NULL COMMENT '文件类型',
    storage_path        VARCHAR(1024) NULL COMMENT '存储路径',
    content_hash        VARCHAR(128) NULL COMMENT '内容哈希',
    file_size           BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
    created_by          BIGINT NOT NULL COMMENT '创建人',
    updated_by          BIGINT NULL COMMENT '更新人',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_generated_file_app_version_id FOREIGN KEY (app_version_id) REFERENCES app_version (id),
    INDEX idx_generated_file_version_path (app_version_id, file_path),
    INDEX idx_generated_file_hash (content_hash)
) COMMENT='生成文件表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 8. 导出与部署
-- =========================

CREATE TABLE export_package (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    app_id              BIGINT NOT NULL COMMENT '应用ID',
    app_version_id      BIGINT NOT NULL COMMENT '应用版本ID',
    package_type        VARCHAR(64) NOT NULL COMMENT '导出包类型',
    storage_path        VARCHAR(1024) NOT NULL COMMENT '存储路径',
    status              VARCHAR(32) NOT NULL DEFAULT 'READY' COMMENT '导出状态',
    created_by          BIGINT NOT NULL COMMENT '创建人',
    updated_by          BIGINT NULL COMMENT '更新人',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted          TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_export_package_app_id FOREIGN KEY (app_id) REFERENCES ai_app (id),
    CONSTRAINT fk_export_package_app_version_id FOREIGN KEY (app_version_id) REFERENCES app_version (id),
    INDEX idx_export_package_app_created_at (app_id, created_at)
) COMMENT='导出包表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE deployment_job (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    app_id                  BIGINT NOT NULL COMMENT '应用ID',
    app_version_id          BIGINT NOT NULL COMMENT '应用版本ID',
    environment_code        VARCHAR(64) NOT NULL COMMENT '环境编码',
    deploy_target           VARCHAR(128) NOT NULL COMMENT '部署目标',
    deploy_status           VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT '部署状态',
    runtime_config_json     JSON NULL COMMENT '运行配置',
    request_id              VARCHAR(128) NULL COMMENT '请求标识',
    started_at              DATETIME NULL COMMENT '开始时间',
    finished_at             DATETIME NULL COMMENT '结束时间',
    created_by              BIGINT NOT NULL COMMENT '创建人',
    updated_by              BIGINT NULL COMMENT '更新人',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted              TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT fk_deployment_job_app_id FOREIGN KEY (app_id) REFERENCES ai_app (id),
    CONSTRAINT fk_deployment_job_app_version_id FOREIGN KEY (app_version_id) REFERENCES app_version (id),
    INDEX idx_deployment_job_app_status (app_id, deploy_status, created_at),
    INDEX idx_deployment_job_request_id (request_id)
) COMMENT='部署任务表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE deployment_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    deployment_job_id   BIGINT NOT NULL COMMENT '部署任务ID',
    log_level           VARCHAR(16) NOT NULL COMMENT '日志级别',
    log_message         TEXT NOT NULL COMMENT '日志内容',
    log_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间',
    CONSTRAINT fk_deployment_log_job_id FOREIGN KEY (deployment_job_id) REFERENCES deployment_job (id),
    INDEX idx_deployment_log_job_time (deployment_job_id, log_time)
) COMMENT='部署日志表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 9. 配额与审计
-- =========================

CREATE TABLE user_quota (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id                 BIGINT NOT NULL COMMENT '用户ID',
    workspace_id            BIGINT NOT NULL COMMENT '工作空间ID',
    daily_request_limit     INT NOT NULL DEFAULT 0 COMMENT '每日请求次数上限',
    daily_token_limit       INT NOT NULL DEFAULT 0 COMMENT '每日Token上限',
    monthly_cost_limit      DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '每月成本上限',
    status                  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '额度状态',
    effective_from          DATETIME NULL COMMENT '生效开始时间',
    effective_to            DATETIME NULL COMMENT '生效结束时间',
    created_by              BIGINT NULL COMMENT '创建人',
    updated_by              BIGINT NULL COMMENT '更新人',
    created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted              TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    CONSTRAINT uk_user_quota UNIQUE (user_id, workspace_id),
    CONSTRAINT fk_user_quota_user_id FOREIGN KEY (user_id) REFERENCES user (id),
    CONSTRAINT fk_user_quota_workspace_id FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    INDEX idx_user_quota_status (status)
) COMMENT='用户额度表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE quota_usage_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    quota_id            BIGINT NOT NULL COMMENT '额度ID',
    task_id             BIGINT NULL COMMENT '任务ID',
    usage_type          VARCHAR(64) NOT NULL COMMENT '使用类型',
    request_count       INT NOT NULL DEFAULT 0 COMMENT '请求次数',
    token_count         INT NOT NULL DEFAULT 0 COMMENT 'Token数量',
    cost_amount         DECIMAL(18, 4) NOT NULL DEFAULT 0 COMMENT '成本金额',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_quota_usage_log_quota_id FOREIGN KEY (quota_id) REFERENCES user_quota (id),
    CONSTRAINT fk_quota_usage_log_task_id FOREIGN KEY (task_id) REFERENCES generation_task (id),
    INDEX idx_quota_usage_log_quota_created_at (quota_id, created_at),
    INDEX idx_quota_usage_log_task_id (task_id)
) COMMENT='额度使用日志表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE audit_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    workspace_id        BIGINT NULL COMMENT '工作空间ID',
    actor_user_id       BIGINT NOT NULL COMMENT '操作人ID',
    action_code         VARCHAR(64) NOT NULL COMMENT '动作编码',
    target_type         VARCHAR(64) NOT NULL COMMENT '目标类型',
    target_id           VARCHAR(128) NOT NULL COMMENT '目标标识',
    request_id          VARCHAR(128) NULL COMMENT '请求标识',
    detail_json         JSON NULL COMMENT '审计详情',
    ip_address          VARCHAR(64) NULL COMMENT 'IP地址',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_audit_log_workspace_id FOREIGN KEY (workspace_id) REFERENCES workspace (id),
    CONSTRAINT fk_audit_log_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES user (id),
    INDEX idx_audit_log_workspace_time (workspace_id, created_at),
    INDEX idx_audit_log_actor_time (actor_user_id, created_at),
    INDEX idx_audit_log_action_code (action_code),
    INDEX idx_audit_log_request_id (request_id)
) COMMENT='审计日志表' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE metric_daily_agg (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    stat_date           DATE NOT NULL COMMENT '统计日期',
    metric_key          VARCHAR(64) NOT NULL COMMENT '指标键',
    metric_value        DECIMAL(18, 4) NOT NULL COMMENT '指标值',
    dimensions_json     JSON NULL COMMENT '维度信息',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_metric_daily_agg UNIQUE (stat_date, metric_key),
    INDEX idx_metric_daily_agg_metric_key (metric_key)
) COMMENT='每日指标聚合表' COLLATE = utf8mb4_unicode_ci;

-- =========================
-- 10. 延迟外键
-- =========================

ALTER TABLE ai_app
    ADD CONSTRAINT fk_ai_app_current_version_id
        FOREIGN KEY (current_version_id) REFERENCES app_version (id);

ALTER TABLE ai_app
    ADD CONSTRAINT fk_ai_app_latest_task_id
        FOREIGN KEY (latest_task_id) REFERENCES generation_task (id);
