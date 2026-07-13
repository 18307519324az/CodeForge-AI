# Release Scripts

本目录包含本地发布验收脚本、Prompt runtime gate、README 截图脚本和 README 资产校验脚本。

## README 截图

截图脚本依赖正在运行的本地前端和后端，通过前端代理调用真实 API。脚本不提供默认账号密码，不写入 storageState、Cookie、Token 或响应明文。

必需环境变量：

- `CODEFORGE_SCREENSHOT_BASE_URL`
- `CODEFORGE_SCREENSHOT_ADMIN_USERNAME`
- `CODEFORGE_SCREENSHOT_ADMIN_PASSWORD`
- `CODEFORGE_SCREENSHOT_USER_USERNAME`
- `CODEFORGE_SCREENSHOT_USER_PASSWORD`

执行：

```powershell
node scripts/release/capture-readme-screenshots.mjs
```

输出：

- `docs/images/01-home-workbench.webp`
- `docs/images/02-generation-workbench.webp`
- `docs/images/03-artifact-preview.webp`
- `docs/images/04-marketplace.webp`
- `docs/images/05-admin-overview.webp`
- `docs/images/06-provider-routing.webp`
- `docs/images/07-prompt-versioning.webp`
- `docs/images/08-model-call-audit.webp`
- `docs/images/codeforge-overview.webp`
- `docs/images/social-preview.png`

## README 资产校验

```powershell
node scripts/release/readme-assets.test.mjs
```

该脚本校验 README 与 docs 中的图片引用、发布图片存在性、旧资源引用、密钥模式和本机路径。

## Prompt Runtime Gate

Prompt runtime gate 用于本地验收固定 Prompt 版本、Artifact 绑定和浏览器刷新证据。它需要显式账号环境变量和已存在的 task/model call，不会调用真实 AI Provider。

```powershell
node scripts/release/prompt-runtime-binding-gate.mjs --task-id <taskId> --model-call-id <modelCallId>
```

## 本地 Schema Gate

```powershell
powershell -File scripts/db/check-local-schema.ps1
```

返回 `READY` 时可继续本地验收。返回 `HISTORY_MISMATCH` 时不要执行 `flyway repair`，不要修改已提交 migration checksum。
