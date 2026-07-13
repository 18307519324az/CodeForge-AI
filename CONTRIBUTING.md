# 贡献指南

感谢关注 CodeForge AI。公开仓库优先接受可审计、可测试、边界清晰的变更。

## 分支与提交

- 使用清晰的分支名和提交信息。
- 不要提交真实 API Key、数据库密码、访问 Token、Cookie、用户数据或本机绝对路径。
- 不要把临时 gate/probe 输出、运行日志、截图草稿或 IDE 私有配置加入提交。

## 本地验证

提交前至少运行：

```powershell
mvn test
Push-Location frontend
npm ci
npm run type-check
npm run test
npm run build
Pop-Location
node --test scripts/release/**/*.test.mjs
git diff --check
```

涉及 README 截图或文档链接时，还需运行：

```powershell
node scripts/release/readme-assets.test.mjs
```

## 变更边界

- 修改权限、Prompt、Provider、Marketplace、Artifact 或 migration 时，必须补充针对性测试。
- 不要修改已应用 migration 的内容来“修复” checksum。
- 生产密钥只允许通过环境变量或受控密钥系统注入。

## Pull Request

PR 描述应包含：

- 变更范围
- 已运行测试
- 安全影响
- 迁移影响
- 截图或文档影响
