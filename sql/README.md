# SQL 目录说明

本目录同时保留“设计草案”和“可执行迁移脚本”，用途必须严格区分。

## 1. 文件分层

1. `ddl_v1_codeforge_ai.sql`
   说明：首版数据库设计草案，便于评审表结构、字段、索引和约束。
   用途：架构评审、字段对照、文档引用。
   执行方式：不作为 Flyway 直接执行入口。

2. `migrations/V1__init.sql`
   说明：首版初始化迁移脚本。
   用途：作为 Flyway 可执行脚本，初始化首版 MVP 所需表结构。
   执行方式：由 Flyway 按版本顺序执行。

3. `create_table.sql`
   说明：旧表结构文件，仅用于读取历史基线。
   用途：旧表映射、迁移对照。
   执行方式：不再作为新版本数据库初始化入口。

## 2. 迁移命名规则

1. 可执行脚本统一放在 `sql/migrations/`
2. 文件名统一使用 `V{版本号}__{说明}.sql`
3. 同一脚本只做一次明确变更，不混入历史草稿内容
4. 不在迁移脚本内执行 `CREATE DATABASE` 或 `USE`

## 3. 当前首版迁移范围

`V1__init.sql` 当前覆盖以下首版表：

1. `user`
2. `user_role`
3. `workspace`
4. `workspace_member`
5. `ai_app`
6. `prompt_template`
7. `prompt_template_version`
8. `model_provider`
9. `generation_task`
10. `generation_task_event`
11. `generation_record`
12. `model_call_log`
13. `app_version`
14. `artifact_snapshot`
15. `generated_file`
16. `export_package`
17. `deployment_job`
18. `deployment_log`
19. `user_quota`
20. `quota_usage_log`
21. `audit_log`
22. `metric_daily_agg`

## 4. 暂未进入 V1 迁移的预留表

以下内容仍保留在领域设计文档中，但未进入当前可执行迁移脚本：

1. `app_page`
2. `page_component`
3. `component_binding`
4. `data_source`
5. `data_entity`
6. `data_field`
7. `model_route_rule`

这些表属于 `Phase 2` 预留范围。后续如进入开发，应新增独立迁移脚本，例如：

1. `V2__page_designer.sql`
2. `V3__data_source.sql`
3. `V4__model_route_rule.sql`
