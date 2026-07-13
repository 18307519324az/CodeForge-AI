# CodeForge AI Frontend

`frontend/` 是 CodeForge AI 智能应用生成与发布平台的前端工程，基于 `Vue 3`、`TypeScript`、`Vite` 与 `Ant Design Vue` 构建。

## 目录说明

- `src/pages/`：页面入口
- `src/components/`：通用组件
- `src/api/`：前端接口封装
- `src/router/`：路由配置
- `src/stores/`：Pinia 状态管理
- `src/config/`：环境变量与地址配置

## 本地开发

```bash
npm install
npm run dev
```

默认开发 API 地址为相对路径 `/api/v1`，由 Vite 将 `/api` 代理到 `http://127.0.0.1:8150`，无需手工设置 `VITE_API_BASE_URL`。

仅在直连远端 API 或自定义后端端口时，在 `.env.local` 中覆盖 `VITE_API_BASE_URL`。

## 模型供应商路由

后端通过 `codeforge.ai.provider`（或环境变量 `AI_PROVIDER`）控制运行时 AI 路由：

- `auto`：使用所有 **ACTIVE** 且 **已配置密钥** 的 AI 供应商，按数据库 `priority` 升序形成调用链
- `deepseek` / `openai` / `qwen` 等：**PIN 模式**，仅使用指定供应商（不可用时不静默切换其它 AI）
- `rule` 供应商不参与 AI 链，仅在全部 AI 失败后作为确定性回退

示例：

```bash
AI_PROVIDER=auto          # 多供应商按 priority 自动 failover
AI_PROVIDER=deepseek      # 固定 DeepSeek
```

## 构建

```bash
npm run build
```

## 接口口径

- 业务接口统一走 `/api/v1`
- 静态预览资源统一走 `/api/static`
- 默认开发代理配置见 `vite.config.ts`
