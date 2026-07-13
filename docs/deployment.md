# 部署说明

## 本地开发部署

1. 使用 `docker compose up -d mysql redis` 启动基础设施。
2. 设置 `.env.local` 或当前 shell 环境变量。
3. 运行 `scripts/db/check-local-schema.ps1` 核验数据库。
4. 运行 `scripts/dev-start.ps1` 启动后端和前端。

## 生产化前需要补齐

当前公开仓库不包含完整生产部署模板。上线前至少需要补齐：

- 独立后端镜像与前端静态资源镜像。
- Nginx 或网关反向代理。
- HTTPS 与安全 Cookie 配置。
- 持久化对象存储或受控文件存储。
- Provider 密钥托管。
- 数据库备份与恢复流程。
- 日志脱敏与集中化审计。

## Docker Compose 边界

根目录 `docker-compose.yml` 只提供 MySQL 和 Redis。它不会构建后端镜像，也不会启动 Vite 前端。
