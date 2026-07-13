-- V2__complete_mvp_tables.sql
-- 补齐 CodeForge AI MVP 必需字段和索引

-- generation_task：新增需求描述和提示词模板关联
ALTER TABLE generation_task
  ADD COLUMN requirement TEXT NULL COMMENT '用户需求描述' AFTER task_type;
ALTER TABLE generation_task
  ADD COLUMN prompt_template_id BIGINT NULL COMMENT '使用的提示词模板ID' AFTER requirement;
ALTER TABLE generation_task
  ADD COLUMN prompt_template_version_no INT NULL COMMENT '模板版本号' AFTER prompt_template_id;

-- prompt_template：新增描述字段
ALTER TABLE prompt_template
  ADD COLUMN description VARCHAR(512) NULL COMMENT '模板描述' AFTER template_scene;

-- prompt_template_version：新增状态字段
ALTER TABLE prompt_template_version
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态' AFTER model_strategy_json;

-- model_call_log：新增创建人
ALTER TABLE model_call_log
  ADD COLUMN created_by BIGINT NULL COMMENT '创建人' AFTER error_message;

-- generated_file：新增文件内容字段（MVP 直存文本）
ALTER TABLE generated_file
  ADD COLUMN file_content MEDIUMTEXT NULL COMMENT '文件内容' AFTER file_size;

-- generation_task 补充索引
CREATE INDEX idx_generation_task_workspace_created
  ON generation_task (workspace_id, created_at);

-- 回填管理员：给第一个注册用户授予 PLATFORM_ADMIN
INSERT IGNORE INTO user_role (user_id, role_code, created_by, updated_by, created_at, updated_at)
SELECT id, 'PLATFORM_ADMIN', id, id, NOW(), NOW()
FROM user
WHERE is_deleted = 0
ORDER BY id ASC
LIMIT 1;
