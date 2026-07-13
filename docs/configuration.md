# 配置说明

所有真实密钥都应通过环境变量或受控密钥系统注入，不应提交到 Git。

| 变量 | 示例 | 说明 |
| --- | --- | --- |
| `DB_HOST` | `127.0.0.1` | MySQL 主机 |
| `DB_PORT` | `3306` | MySQL 端口 |
| `DB_NAME` | `codeforge_ai` | 数据库名 |
| `DB_USERNAME` | `codeforge_ai_user` | 应用数据库用户 |
| `DB_PASSWORD` | `<your-db-password>` | 应用数据库密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | `<optional>` | Redis 密码 |
| `JWT_SECRET` | `<at-least-32-characters>` | JWT 签名密钥 |
| `AI_PROVIDER` | `auto` | Provider 路由模式 |
| `OPENAI_API_KEY` | `<openai-api-key>` | OpenAI-compatible 密钥 |
| `DEEPSEEK_API_KEY` | `<deepseek-api-key>` | DeepSeek 密钥 |
| `CODEFORGE_CREDENTIAL_MASTER_KEY` | `<32-byte-secret>` | 数据库加密凭据主密钥 |
| `CODEFORGE_FORCE_RULE_ONLY` | `true` | 强制规则生成，不调用真实模型 |

## 本地开发建议

- 默认使用 `CODEFORGE_FORCE_RULE_ONLY=true` 完成 UI、权限和产物链路验证。
- 需要真实模型调用时，只在当前 shell 注入 Provider 密钥。
- 不要把 `.env.local`、截图、日志或命令历史中的密钥提交到仓库。
