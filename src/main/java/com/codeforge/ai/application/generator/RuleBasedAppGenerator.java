package com.codeforge.ai.application.generator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则生成器：不依赖外部 AI 模型，根据需求描述生成基础应用文件。
 * 用于 MVP 演示闭环。
 */
public class RuleBasedAppGenerator {

    public record GeneratedFile(String filePath, String fileName, String content) {}

    public GeneratedProject generate(String appName, String appType, String requirement) {
        String safeName = sanitizeName(appName);
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String desc = requirement != null && !requirement.isBlank() ? requirement : "自动生成的应用";

        List<GeneratedFile> files = new ArrayList<>();

        files.add(new GeneratedFile("index.html", "index.html",
            """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>%s</title>
              <style>
                :root {
                  color-scheme: light;
                  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  min-height: 100vh;
                  background: linear-gradient(180deg, #f7f9fc 0%%, #eef4ff 100%%);
                  color: #0f172a;
                }
                .page {
                  max-width: 920px;
                  margin: 0 auto;
                  padding: 48px 24px 64px;
                }
                .hero {
                  background: rgba(255, 255, 255, 0.94);
                  border: 1px solid rgba(148, 163, 184, 0.22);
                  border-radius: 24px;
                  padding: 32px;
                  box-shadow: 0 20px 45px rgba(15, 23, 42, 0.08);
                }
                .badge {
                  display: inline-flex;
                  align-items: center;
                  padding: 6px 12px;
                  border-radius: 999px;
                  background: #dbeafe;
                  color: #1d4ed8;
                  font-size: 13px;
                  font-weight: 600;
                }
                h1 {
                  margin: 18px 0 12px;
                  font-size: 36px;
                  line-height: 1.15;
                }
                .lead {
                  margin: 0;
                  color: #475569;
                  font-size: 16px;
                  line-height: 1.7;
                }
                .meta {
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                  gap: 16px;
                  margin-top: 28px;
                }
                .meta-card {
                  padding: 18px;
                  border-radius: 18px;
                  background: #f8fafc;
                  border: 1px solid #e2e8f0;
                }
                .meta-card span {
                  display: block;
                  margin-bottom: 8px;
                  color: #64748b;
                  font-size: 12px;
                  text-transform: uppercase;
                  letter-spacing: 0.08em;
                }
                .meta-card strong {
                  font-size: 16px;
                }
                .footer {
                  margin-top: 28px;
                  color: #64748b;
                  font-size: 14px;
                }
              </style>
            </head>
            <body>
              <main class="page">
                <section class="hero">
                  <div class="badge">CodeForge AI Preview</div>
                  <h1>%s</h1>
                  <p class="lead">%s</p>
                  <div class="meta">
                    <article class="meta-card">
                      <span>应用类型</span>
                      <strong>%s</strong>
                    </article>
                    <article class="meta-card">
                      <span>生成时间</span>
                      <strong>%s</strong>
                    </article>
                    <article class="meta-card">
                      <span>预览说明</span>
                      <strong>当前为规则生成预览页</strong>
                    </article>
                  </div>
                  <p class="footer">该页面用于本地预览当前版本文件内容，下载源码后可继续开发。</p>
                </section>
              </main>
            </body>
            </html>
            """.formatted(appName, appName, escapeHtml(desc), appType, now)
        ));

        // README.md
        files.add(new GeneratedFile("README.md", "README.md",
            "# " + appName + "\n\n" +
            "## 需求描述\n\n" + desc + "\n\n" +
            "## 生成内容\n\n" +
            "- 应用入口：`src/App.vue`\n" +
            "- 入口脚本：`src/main.ts`\n" +
            "- 项目配置：`package.json`\n\n" +
            "## 启动方式\n\n" +
            "```bash\nnpm install\nnpm run dev\n```\n\n" +
            "## 生成信息\n\n" +
            "- 应用类型：" + appType + "\n" +
            "- 生成时间：" + now + "\n" +
            "- 生成引擎：CodeForge AI Rule Generator\n"
        ));

        // package.json
        files.add(new GeneratedFile("package.json", "package.json",
            "{\n" +
            "  \"name\": \"" + safeName + "\",\n" +
            "  \"version\": \"0.1.0\",\n" +
            "  \"private\": true,\n" +
            "  \"scripts\": {\n" +
            "    \"dev\": \"vite\",\n" +
            "    \"build\": \"vite build\"\n" +
            "  },\n" +
            "  \"dependencies\": {\n" +
            "    \"vue\": \"^3.4.0\"\n" +
            "  },\n" +
            "  \"devDependencies\": {\n" +
            "    \"@vitejs/plugin-vue\": \"^5.0.0\",\n" +
            "    \"vite\": \"^5.0.0\"\n" +
            "  }\n" +
            "}\n"
        ));

        // src/main.ts
        files.add(new GeneratedFile("src/main.ts", "main.ts",
            "import { createApp } from 'vue'\n" +
            "import App from './App.vue'\n\n" +
            "createApp(App).mount('#app')\n"
        ));

        // src/App.vue with requirement-aware content
        files.add(new GeneratedFile("src/App.vue", "App.vue",
            "<template>\n" +
            "  <div id=\"app\">\n" +
            "    <header class=\"app-header\">\n" +
            "      <h1>" + appName + "</h1>\n" +
            "      <p>" + desc + "</p>\n" +
            "    </header>\n" +
            "    <main class=\"app-main\">\n" +
            "      <p>欢迎使用 " + appName + "，此应用由 CodeForge AI 自动生成。</p>\n" +
            "    </main>\n" +
            "  </div>\n" +
            "</template>\n\n" +
            "<script setup lang=\"ts\">\n" +
            "// " + appName + " — 自动生成于 " + now + "\n" +
            "</script>\n\n" +
            "<style scoped>\n" +
            "#app { font-family: system-ui, sans-serif; max-width: 800px; margin: 0 auto; padding: 24px; }\n" +
            ".app-header { margin-bottom: 24px; }\n" +
            ".app-header h1 { font-size: 24px; margin: 0 0 8px; }\n" +
            ".app-header p { color: #666; margin: 0; }\n" +
            "</style>\n"
        ));

        return new GeneratedProject(safeName, appType, desc, files);
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "generated-app";
        return name.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String escapeHtml(String value) {
        if (value == null || value.isBlank()) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record GeneratedProject(
            String appName,
            String appType,
            String requirement,
            List<GeneratedFile> files
    ) {}
}
