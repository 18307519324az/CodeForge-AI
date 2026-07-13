-- CodeForge MySQL V33 baseline schema
-- Fresh environments apply this baseline migration instead of V1..V33 versioned migrations.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `account` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录账号',
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希',
  `display_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '显示名称',
  `avatar_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像地址',
  `email` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态',
  `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`account`),
  UNIQUE KEY `uk_user_email` (`email`),
  KEY `idx_user_status` (`status`),
  KEY `idx_user_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '平台角色编码',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_code`),
  KEY `idx_user_role_role_code` (`role_code`),
  CONSTRAINT `fk_user_role_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户平台角色表';

CREATE TABLE IF NOT EXISTS `user_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `workspace_id` bigint NOT NULL COMMENT '工作空间ID',
  `daily_request_limit` int NOT NULL DEFAULT '0' COMMENT '每日请求次数上限',
  `daily_token_limit` int NOT NULL DEFAULT '0' COMMENT '每日Token上限',
  `monthly_cost_limit` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '每月成本上限',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '额度状态',
  `effective_from` datetime DEFAULT NULL COMMENT '生效开始时间',
  `effective_to` datetime DEFAULT NULL COMMENT '生效结束时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_quota` (`user_id`,`workspace_id`),
  KEY `fk_user_quota_workspace_id` (`workspace_id`),
  KEY `idx_user_quota_status` (`status`),
  CONSTRAINT `fk_user_quota_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_user_quota_workspace_id` FOREIGN KEY (`workspace_id`) REFERENCES `workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户额度表';

CREATE TABLE IF NOT EXISTS `quota_usage_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `quota_id` bigint NOT NULL COMMENT '额度ID',
  `task_id` bigint DEFAULT NULL COMMENT '任务ID',
  `usage_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '使用类型',
  `request_count` int NOT NULL DEFAULT '0' COMMENT '请求次数',
  `token_count` int NOT NULL DEFAULT '0' COMMENT 'Token数量',
  `cost_amount` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '成本金额',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_quota_usage_log_quota_created_at` (`quota_id`,`created_at`),
  KEY `idx_quota_usage_log_task_id` (`task_id`),
  CONSTRAINT `fk_quota_usage_log_quota_id` FOREIGN KEY (`quota_id`) REFERENCES `user_quota` (`id`),
  CONSTRAINT `fk_quota_usage_log_task_id` FOREIGN KEY (`task_id`) REFERENCES `generation_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='额度使用日志表';

CREATE TABLE IF NOT EXISTS `workspace` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '空间名称',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '空间描述',
  `owner_user_id` bigint NOT NULL COMMENT '拥有者用户ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '空间状态',
  `plan_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FREE' COMMENT '套餐编码',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workspace_owner_name` (`owner_user_id`,`name`),
  KEY `idx_workspace_owner_user_id` (`owner_user_id`),
  KEY `idx_workspace_status` (`status`),
  CONSTRAINT `fk_workspace_owner_user_id` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作空间表';

CREATE TABLE IF NOT EXISTS `workspace_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint NOT NULL COMMENT '工作空间ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `member_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '空间成员角色',
  `member_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '成员状态',
  `invited_by` bigint DEFAULT NULL COMMENT '邀请人ID',
  `joined_at` datetime DEFAULT NULL COMMENT '加入时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workspace_member` (`workspace_id`,`user_id`),
  KEY `fk_workspace_member_user_id` (`user_id`),
  KEY `idx_workspace_member_role` (`member_role`),
  KEY `idx_workspace_member_status` (`member_status`),
  CONSTRAINT `fk_workspace_member_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_workspace_member_workspace_id` FOREIGN KEY (`workspace_id`) REFERENCES `workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作空间成员表';

CREATE TABLE IF NOT EXISTS `ai_app` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint NOT NULL COMMENT '工作空间ID',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用名称',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '应用描述',
  `cover_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '封面地址',
  `app_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '应用类型',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '应用状态',
  `visibility` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRIVATE' COMMENT '可见性',
  `current_version_id` bigint DEFAULT NULL COMMENT '当前版本ID',
  `latest_task_id` bigint DEFAULT NULL COMMENT '最近任务ID',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `published_at` datetime DEFAULT NULL,
  `unpublished_at` datetime DEFAULT NULL,
  `deploy_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deployed_at` datetime DEFAULT NULL,
  `view_count` bigint NOT NULL DEFAULT '0',
  `like_count` bigint NOT NULL DEFAULT '0',
  `fork_count` bigint NOT NULL DEFAULT '0',
  `published_version_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_app_workspace_status` (`workspace_id`,`status`,`updated_at`),
  KEY `idx_ai_app_created_by` (`created_by`),
  KEY `idx_ai_app_visibility` (`visibility`),
  KEY `fk_ai_app_current_version_id` (`current_version_id`),
  KEY `fk_ai_app_latest_task_id` (`latest_task_id`),
  CONSTRAINT `fk_ai_app_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_ai_app_current_version_id` FOREIGN KEY (`current_version_id`) REFERENCES `app_version` (`id`),
  CONSTRAINT `fk_ai_app_latest_task_id` FOREIGN KEY (`latest_task_id`) REFERENCES `generation_task` (`id`),
  CONSTRAINT `fk_ai_app_workspace_id` FOREIGN KEY (`workspace_id`) REFERENCES `workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI应用表';

CREATE TABLE IF NOT EXISTS `app_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `version_source` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本来源',
  `source_task_id` bigint DEFAULT NULL COMMENT '来源任务ID',
  `change_summary` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更摘要',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '版本状态',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `preview_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `preview_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `build_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_BUILT',
  `build_log` text COLLATE utf8mb4_unicode_ci,
  `built_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_version` (`app_id`,`version_no`),
  KEY `fk_app_version_source_task_id` (`source_task_id`),
  KEY `idx_app_version_app_version_no` (`app_id`,`version_no`),
  KEY `idx_app_version_created_at` (`created_at`),
  CONSTRAINT `fk_app_version_app_id` FOREIGN KEY (`app_id`) REFERENCES `ai_app` (`id`),
  CONSTRAINT `fk_app_version_source_task_id` FOREIGN KEY (`source_task_id`) REFERENCES `generation_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用版本表';

CREATE TABLE IF NOT EXISTS `artifact_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_version_id` bigint NOT NULL COMMENT '应用版本ID',
  `snapshot_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '快照类型',
  `snapshot_path` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '快照路径',
  `snapshot_content_json` json DEFAULT NULL COMMENT '快照内容',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_artifact_snapshot_version_type` (`app_version_id`,`snapshot_type`),
  CONSTRAINT `fk_artifact_snapshot_app_version_id` FOREIGN KEY (`app_version_id`) REFERENCES `app_version` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='制品快照表';

CREATE TABLE IF NOT EXISTS `generated_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_version_id` bigint NOT NULL COMMENT '应用版本ID',
  `file_path` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件路径',
  `file_name` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `file_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件类型',
  `storage_path` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '存储路径',
  `content_hash` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '内容哈希',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小',
  `file_content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '文件内容',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_generated_file_version_path` (`app_version_id`,`file_path`(255)),
  KEY `idx_generated_file_hash` (`content_hash`),
  CONSTRAINT `fk_generated_file_app_version_id` FOREIGN KEY (`app_version_id`) REFERENCES `app_version` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成文件表';

CREATE TABLE IF NOT EXISTS `export_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `app_version_id` bigint NOT NULL COMMENT '应用版本ID',
  `package_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '导出包类型',
  `storage_path` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '存储路径',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'READY' COMMENT '导出状态',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `fk_export_package_app_version_id` (`app_version_id`),
  KEY `idx_export_package_app_created_at` (`app_id`,`created_at`),
  CONSTRAINT `fk_export_package_app_id` FOREIGN KEY (`app_id`) REFERENCES `ai_app` (`id`),
  CONSTRAINT `fk_export_package_app_version_id` FOREIGN KEY (`app_version_id`) REFERENCES `app_version` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='导出包表';

CREATE TABLE IF NOT EXISTS `app_publication` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` bigint NOT NULL,
  `version_id` bigint NOT NULL,
  `publisher_user_id` bigint NOT NULL,
  `public_title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `public_description` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `slug` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `allow_preview` tinyint NOT NULL DEFAULT '1',
  `allow_download` tinyint NOT NULL DEFAULT '0',
  `published_at` datetime DEFAULT NULL,
  `unpublished_at` datetime DEFAULT NULL,
  `view_count` bigint NOT NULL DEFAULT '0',
  `download_count` bigint NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_publication_app_id` (`app_id`),
  UNIQUE KEY `uk_app_publication_slug` (`slug`),
  KEY `idx_app_publication_status_published_at` (`status`,`published_at` DESC,`id` DESC),
  KEY `idx_app_publication_publisher_user_id` (`publisher_user_id`),
  KEY `idx_app_publication_version_id` (`version_id`),
  CONSTRAINT `fk_app_publication_app_id` FOREIGN KEY (`app_id`) REFERENCES `ai_app` (`id`),
  CONSTRAINT `fk_app_publication_publisher_user_id` FOREIGN KEY (`publisher_user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `app_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `app_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user` (`app_id`,`user_id`),
  KEY `idx_app_id` (`app_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `publication_view_dedupe` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??',
  `publication_id` bigint NOT NULL COMMENT '????ID',
  `viewer_key_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???????',
  `last_counted_at` datetime(3) NOT NULL COMMENT '???????',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '????',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_publication_view_dedupe_pub_hash` (`publication_id`,`viewer_key_hash`),
  KEY `idx_publication_view_dedupe_pub` (`publication_id`),
  KEY `idx_publication_view_dedupe_last_counted` (`last_counted_at`),
  CONSTRAINT `fk_publication_view_dedupe_publication_id` FOREIGN KEY (`publication_id`) REFERENCES `app_publication` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='??????????';

CREATE TABLE IF NOT EXISTS `generation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint NOT NULL COMMENT '工作空间ID',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `task_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务类型',
  `requirement` text COLLATE utf8mb4_unicode_ci COMMENT '用户需求描述',
  `prompt_template_id` bigint DEFAULT NULL COMMENT '使用的提示词模板ID',
  `prompt_template_version_no` int DEFAULT NULL COMMENT '模板版本号',
  `task_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'QUEUED' COMMENT '任务状态',
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '幂等键',
  `retry_of_task_id` bigint DEFAULT NULL COMMENT '重试来源任务ID',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '当前重试次数',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间',
  `request_payload_json` json DEFAULT NULL COMMENT '请求载荷',
  `result_summary_json` json DEFAULT NULL COMMENT '结果摘要',
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求标识',
  `error_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误码',
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `queued_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '完成时间',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `prompt_template_version_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_generation_task_scope_idem_key` (`workspace_id`,`app_id`,`idempotency_key`),
  KEY `fk_generation_task_created_by` (`created_by`),
  KEY `fk_generation_task_retry_of_task_id` (`retry_of_task_id`),
  KEY `idx_generation_task_app_status` (`app_id`,`task_status`,`created_at`),
  KEY `idx_generation_task_workspace_status` (`workspace_id`,`task_status`,`created_at`),
  KEY `idx_generation_task_request_id` (`request_id`),
  KEY `idx_generation_task_retry_next` (`task_status`,`next_retry_at`),
  KEY `idx_generation_task_prompt_template` (`prompt_template_id`),
  KEY `idx_generation_task_prompt_template_version` (`prompt_template_version_id`),
  CONSTRAINT `fk_generation_task_app_id` FOREIGN KEY (`app_id`) REFERENCES `ai_app` (`id`),
  CONSTRAINT `fk_generation_task_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_generation_task_retry_of_task_id` FOREIGN KEY (`retry_of_task_id`) REFERENCES `generation_task` (`id`),
  CONSTRAINT `fk_generation_task_workspace_id` FOREIGN KEY (`workspace_id`) REFERENCES `workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成任务表';

CREATE TABLE IF NOT EXISTS `generation_task_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '事件类型',
  `event_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '事件消息',
  `event_payload_json` json DEFAULT NULL COMMENT '事件载荷',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求ID',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_generation_task_event_task_created` (`task_id`,`created_at`),
  KEY `idx_generation_task_event_type` (`event_type`),
  KEY `idx_generation_task_event_request_id` (`request_id`),
  CONSTRAINT `fk_generation_task_event_task_id` FOREIGN KEY (`task_id`) REFERENCES `generation_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成任务事件表';

CREATE TABLE IF NOT EXISTS `generation_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint NOT NULL COMMENT '工作空间ID',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '生成状态',
  `prompt_template_version_id` bigint DEFAULT NULL COMMENT '提示词模板版本ID',
  `model_provider_id` bigint DEFAULT NULL COMMENT '模型供应商ID',
  `model_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型名称',
  `input_summary` text COLLATE utf8mb4_unicode_ci COMMENT '输入摘要',
  `output_summary` text COLLATE utf8mb4_unicode_ci COMMENT '输出摘要',
  `token_input` int NOT NULL DEFAULT '0' COMMENT '输入Token',
  `token_output` int NOT NULL DEFAULT '0' COMMENT '输出Token',
  `duration_ms` bigint NOT NULL DEFAULT '0' COMMENT '耗时毫秒',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `fk_generation_record_workspace_id` (`workspace_id`),
  KEY `fk_generation_record_prompt_template_version_id` (`prompt_template_version_id`),
  KEY `fk_generation_record_model_provider_id` (`model_provider_id`),
  KEY `idx_generation_record_app_created_at` (`app_id`,`created_at`),
  KEY `idx_generation_record_task_id` (`task_id`),
  CONSTRAINT `fk_generation_record_app_id` FOREIGN KEY (`app_id`) REFERENCES `ai_app` (`id`),
  CONSTRAINT `fk_generation_record_model_provider_id` FOREIGN KEY (`model_provider_id`) REFERENCES `model_provider` (`id`),
  CONSTRAINT `fk_generation_record_prompt_template_version_id` FOREIGN KEY (`prompt_template_version_id`) REFERENCES `prompt_template_version` (`id`),
  CONSTRAINT `fk_generation_record_task_id` FOREIGN KEY (`task_id`) REFERENCES `generation_task` (`id`),
  CONSTRAINT `fk_generation_record_workspace_id` FOREIGN KEY (`workspace_id`) REFERENCES `workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成记录表';

CREATE TABLE IF NOT EXISTS `generation_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `workspace_id` bigint NOT NULL,
  `app_id` bigint DEFAULT NULL,
  `task_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `message_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
  `message_content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `message_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT 'TEXT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_msg_app_created` (`app_id`,`created_at`),
  KEY `idx_msg_task_created` (`task_id`,`created_at`),
  KEY `idx_msg_user_created` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成对话消息';

CREATE TABLE IF NOT EXISTS `model_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `provider_id` bigint NOT NULL COMMENT '供应商ID',
  `provider_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `api_protocol` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求标识',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '调用状态',
  `input_tokens` int NOT NULL DEFAULT '0' COMMENT '输入Token',
  `output_tokens` int NOT NULL DEFAULT '0' COMMENT '输出Token',
  `duration_ms` bigint NOT NULL DEFAULT '0' COMMENT '耗时毫秒',
  `fallback_used` tinyint DEFAULT '0',
  `generation_source` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `app_id` bigint DEFAULT NULL,
  `session_id` bigint DEFAULT NULL,
  `prompt_template_version_id` bigint DEFAULT NULL,
  `prompt_template_code` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `prompt_template_version_no` int DEFAULT NULL,
  `system_prompt_sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_prompt_sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `combined_prompt_fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_model_call_log_provider_id` (`provider_id`),
  KEY `idx_model_call_log_task_provider` (`task_id`,`provider_id`),
  KEY `idx_model_call_log_request_id` (`request_id`),
  KEY `idx_model_call_log_created_at` (`created_at`),
  KEY `idx_model_call_log_app_id` (`app_id`),
  KEY `idx_model_call_log_session_id` (`session_id`),
  KEY `idx_model_call_log_prompt_version` (`prompt_template_version_id`),
  CONSTRAINT `fk_model_call_log_provider_id` FOREIGN KEY (`provider_id`) REFERENCES `model_provider` (`id`),
  CONSTRAINT `fk_model_call_log_task_id` FOREIGN KEY (`task_id`) REFERENCES `generation_task` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用日志表';

CREATE TABLE IF NOT EXISTS `model_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `provider_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商编码',
  `provider_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供应商名称',
  `base_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接口基础地址',
  `auth_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'API_KEY' COMMENT '认证模式',
  `api_protocol` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT 'RULE_BASED' COMMENT 'API协议',
  `secret_ref` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '密钥引用',
  `api_key_env` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '环境变量名',
  `default_model` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认模型名',
  `priority` int NOT NULL DEFAULT '100' COMMENT '优先级',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `credential_source` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENV',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_provider_code` (`provider_code`),
  KEY `idx_model_provider_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型供应商表';

CREATE TABLE IF NOT EXISTS `model_provider_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `provider_id` bigint NOT NULL,
  `credential_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'API_KEY',
  `ciphertext` blob NOT NULL,
  `nonce` blob NOT NULL,
  `key_version` int NOT NULL DEFAULT '1',
  `masked_hint` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_provider_credential_provider` (`provider_id`),
  CONSTRAINT `fk_model_provider_credential_provider` FOREIGN KEY (`provider_id`) REFERENCES `model_provider` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ai_routing_config` (
  `id` bigint NOT NULL,
  `routing_mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AUTO',
  `pinned_provider_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `prompt_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint NOT NULL COMMENT '工作空间ID',
  `template_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `template_scene` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板场景',
  `description` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板描述',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '模板状态',
  `current_version_no` int NOT NULL DEFAULT '1' COMMENT '当前版本号',
  `remark` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_template_name` (`workspace_id`,`template_name`),
  KEY `fk_prompt_template_created_by` (`created_by`),
  KEY `idx_prompt_template_scene_status` (`template_scene`,`status`),
  CONSTRAINT `fk_prompt_template_created_by` FOREIGN KEY (`created_by`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_prompt_template_workspace_id` FOREIGN KEY (`workspace_id`) REFERENCES `workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词模板表';

CREATE TABLE IF NOT EXISTS `prompt_template_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_id` bigint NOT NULL COMMENT '模板ID',
  `version_no` int NOT NULL COMMENT '版本号',
  `system_prompt` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统提示词',
  `user_prompt` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户提示词',
  `variables_json` json DEFAULT NULL COMMENT '变量定义',
  `model_strategy_json` json DEFAULT NULL COMMENT '模型策略定义',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '版本状态',
  `published_by` bigint DEFAULT NULL COMMENT '发布人',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_template_version` (`template_id`,`version_no`),
  KEY `idx_prompt_template_version_published_at` (`published_at`),
  CONSTRAINT `fk_prompt_template_version_template_id` FOREIGN KEY (`template_id`) REFERENCES `prompt_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提示词模板版本表';

CREATE TABLE IF NOT EXISTS `deployment_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `app_id` bigint NOT NULL COMMENT '应用ID',
  `app_version_id` bigint NOT NULL COMMENT '应用版本ID',
  `environment_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '环境编码',
  `deploy_target` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部署目标',
  `deploy_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'QUEUED' COMMENT '部署状态',
  `runtime_config_json` json DEFAULT NULL COMMENT '运行配置',
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求标识',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `created_by` bigint NOT NULL COMMENT '创建人',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `fk_deployment_job_app_version_id` (`app_version_id`),
  KEY `idx_deployment_job_app_status` (`app_id`,`deploy_status`,`created_at`),
  KEY `idx_deployment_job_request_id` (`request_id`),
  CONSTRAINT `fk_deployment_job_app_id` FOREIGN KEY (`app_id`) REFERENCES `ai_app` (`id`),
  CONSTRAINT `fk_deployment_job_app_version_id` FOREIGN KEY (`app_version_id`) REFERENCES `app_version` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署任务表';

CREATE TABLE IF NOT EXISTS `deployment_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `deployment_job_id` bigint NOT NULL COMMENT '部署任务ID',
  `log_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志级别',
  `log_message` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志内容',
  `log_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '日志时间',
  PRIMARY KEY (`id`),
  KEY `idx_deployment_log_job_time` (`deployment_job_id`,`log_time`),
  CONSTRAINT `fk_deployment_log_job_id` FOREIGN KEY (`deployment_job_id`) REFERENCES `deployment_job` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署日志表';

CREATE TABLE IF NOT EXISTS `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `workspace_id` bigint DEFAULT NULL COMMENT '工作空间ID',
  `actor_user_id` bigint NOT NULL COMMENT '操作人ID',
  `action_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作编码',
  `target_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标类型',
  `target_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标标识',
  `request_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求标识',
  `detail_json` json DEFAULT NULL COMMENT '审计详情',
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'IP地址',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_log_workspace_time` (`workspace_id`,`created_at`),
  KEY `idx_audit_log_actor_time` (`actor_user_id`,`created_at`),
  KEY `idx_audit_log_action_code` (`action_code`),
  KEY `idx_audit_log_request_id` (`request_id`),
  CONSTRAINT `fk_audit_log_actor_user_id` FOREIGN KEY (`actor_user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_audit_log_workspace_id` FOREIGN KEY (`workspace_id`) REFERENCES `workspace` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

CREATE TABLE IF NOT EXISTS `metric_daily_agg` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `metric_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '指标键',
  `metric_value` decimal(18,4) NOT NULL COMMENT '指标值',
  `dimensions_json` json DEFAULT NULL COMMENT '维度信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metric_daily_agg` (`stat_date`,`metric_key`),
  KEY `idx_metric_daily_agg_metric_key` (`metric_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日指标聚合表';

SET FOREIGN_KEY_CHECKS = 1;

-- Baseline seed allowlist: routing configuration required by V32 contract
INSERT INTO ai_routing_config (id, routing_mode, pinned_provider_code, updated_by, is_deleted)
SELECT 1, 'AUTO', NULL, NULL, 0
WHERE NOT EXISTS (SELECT 1 FROM ai_routing_config WHERE id = 1);

-- Baseline seed allowlist: rule provider contract (credential_source=NONE, active)
INSERT INTO model_provider (provider_code, provider_name, base_url, auth_mode, api_protocol, api_key_env, default_model, priority, status, credential_source, created_by, updated_by, is_deleted)
SELECT 'rule', 'Rule Generator', NULL, 'NONE', 'RULE_BASED', NULL, 'rule-based', 999, 'ACTIVE', 'NONE', 0, 0, 0
WHERE NOT EXISTS (SELECT 1 FROM model_provider WHERE provider_code = 'rule' AND is_deleted = 0);

-- Baseline seed allowlist: auto provider disabled placeholder required by V32 contract
INSERT INTO model_provider (provider_code, provider_name, base_url, auth_mode, api_protocol, api_key_env, default_model, priority, status, credential_source, created_by, updated_by, is_deleted)
SELECT 'auto', 'Auto Provider', NULL, 'NONE', 'RULE_BASED', NULL, 'auto', 1000, 'DISABLED', 'ENV', 0, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM model_provider WHERE provider_code = 'auto');
