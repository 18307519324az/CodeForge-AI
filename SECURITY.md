# 安全策略

## 支持版本

当前公开版本为 `v1.0.0`。安全修复优先面向最新公开版本。

## 报告方式

请通过 GitHub Security Advisory 或 Issue 报告安全问题。报告中请包含：

- 影响范围
- 复现步骤
- 预期行为与实际行为
- 已确认不会包含真实密钥、Token、Cookie 或用户隐私数据的证据

## 敏感信息处理

- 不要在公开 Issue、PR、截图或日志中发布真实密钥、数据库密码、JWT、访问 Token、Cookie 或系统 Prompt 明文。
- 发现泄露后应立即吊销对应凭据，并在修复中移除泄露内容和传播路径。
- 示例配置只能使用占位值。

## 关注边界

安全评审重点包括：

- IDOR 与跨租户访问
- Prompt 明文泄露
- Provider 凭据泄露
- path traversal 与 zip slip
- 导出包、市场发布和静态预览的版本绑定
- migration checksum 与 schema drift
