# 安全模型

## 授权边界

应用、应用版本、生成文件、导出包和市场发布条目按 Owner/Editor/Admin 权限校验。公开市场接口只能访问已发布且未归档的条目。

## Prompt 与模型调用

生成任务持久化 `templateId` 和 `templateVersionId`。模型调用日志记录模板身份、调用状态和最终 outgoing prompt 的指纹，不向普通 API 返回完整系统 Prompt。

## 路径与文件

生成产物路径需要逐段校验，拒绝 `..`、绝对路径、Windows drive、UNC 和 NUL。最终路径需要经过 normalize 并确认仍在版本根目录内，符号链接逃逸应被拒绝。

## 导出与市场

导出包绑定应用版本，市场发布条目固定 `versionId`。preview、detail 和 download 读取同一个 pinned version，并在读取时检查 archived/unpublished 状态。

## 审计日志

审计日志用于记录发布、撤销、归档、Provider 变更和管理操作。日志不应包含完整 Prompt、密钥、Token、服务端路径或用户密码。
