# Product Tour

## 1. 首页工作台

![首页工作台](images/01-home-workbench.webp)

用户进入工作台后可以查看应用、选择模板、创建生成任务并进入版本文件浏览。

## 2. 模板选择

Prompt Template 管理支持 V1/V2 并存发布。普通用户只能选择可用的 published 版本。

## 3. 生成任务

![生成任务](images/02-generation-workbench.webp)

生成任务写入 `templateId` 和 `templateVersionId`，并记录请求 payload 作为审计证据。

## 4. SSE 里程碑

任务通过事件流展示排队、模型调用、文件生成、版本创建和 `TASK_SUCCESS`。成功事件携带 `versionId`。

## 5. 生成网站预览

![生成网站预览](images/03-generated-site-preview.webp)

该页面来自真实 `generation_task`，文件存储在 `app_version` 关联的 `generated_file` 中，并通过 Preview Token 加载。它不是手工 mock 页面，也不是文件浏览器截图。预览不会向普通 API 暴露服务端 storage path。

## 6. 文件与版本浏览

![文件与版本浏览](images/04-artifact-workbench.webp)

版本文件列表按 app/version 边界读取。文件内容 API 只接受版本内相对路径。

## 7. Artifact Repair

Repair API 修复历史内容时创建新版本，不覆盖源版本。目标路径和源路径都必须落在版本根目录内。

## 8. Export

导出包按 app、version 和 package 绑定。下载时使用固定版本并检查 archived/unpublished 状态。

## 9. Marketplace

![应用市场](images/05-marketplace.webp)

发布条目固定 `versionId`。详情、预览和下载读取同一个 pinned version。

## 10. Admin Overview

![管理概览](images/06-admin-overview.webp)

管理员查看用户、应用、Provider、模型调用和审计聚合，不读取完整系统 Prompt 或密钥。

## 11. Provider AUTO/PIN

![模型路由](images/07-provider-routing.webp)

Provider Routing 支持 Rule Mode、本地演示、AUTO 和 PIN 策略。真实 Provider Key 只在运行时环境中注入。

## 12. Prompt Template V1/V2

![提示词版本](images/08-prompt-versioning.webp)

V1/V2 可以同时处于 published 语义。latest pointer 指向最大有效 published versionNo。

## 13. Model Call Audit

![模型调用审计](images/09-model-call-audit.webp)

审计记录模型来源、状态、token、耗时、模板身份和 prompt fingerprint，不暴露完整 Prompt。

## 14. User B 隔离

User B 只能访问自己的工作区、应用、任务、预览和导出包。跨用户直接对象引用会被拒绝。

## 15. Anonymous 边界

匿名访问只允许公开市场和公开预览入口，不能读取私有应用、生成任务或模型调用日志。
