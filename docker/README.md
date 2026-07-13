# Docker 目录说明

当前仓库的根目录 `docker-compose.yml` 只用于启动本地基础设施：

- MySQL 8.0
- Redis 7

它不会构建或启动 Spring Boot 后端，也不会构建或启动 Vue 前端。

## 本地基础设施启动

```powershell
docker compose up -d mysql redis
```

后端仍从仓库根目录通过 Maven Wrapper 启动：

```powershell
powershell -File .\scripts\dev-start.ps1 -Profile local -BackendPort 8150 -FrontendPort 5182
```

## 后续生产化缺口

若要生产部署，需要补充独立的后端镜像、前端静态资源镜像、反向代理配置、HTTPS、对象存储、密钥托管和日志审计链路。
