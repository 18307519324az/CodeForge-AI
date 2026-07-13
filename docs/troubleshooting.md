# 故障排查

## Schema Gate 返回 MISSING

表示当前数据库缺少必需对象或 Flyway history。全新本地库可先运行：

```powershell
powershell -File .\scripts\db\apply-local-migrations.ps1
powershell -File .\scripts\db\check-local-schema.ps1
```

## Schema Gate 返回 HISTORY_MISMATCH

表示 schema 和 history 不一致。不要执行 `flyway repair`，也不要修改已提交 migration 的 checksum。请先比对目标库 history 与当前 `sql/` 目录。

## 前端无法调用后端

确认 `scripts/dev-start.ps1` 输出的端口：

- 后端：`http://127.0.0.1:8150/api`
- 前端：`http://127.0.0.1:5182`

确认前端启动时注入的 `VITE_API_BASE_URL` 指向后端 `/api/v1`。

## 真实模型调用失败

先设置 `CODEFORGE_FORCE_RULE_ONLY=true` 验证业务链路。若规则生成可用，再检查 Provider 状态、路由策略和对应 API Key 环境变量。
