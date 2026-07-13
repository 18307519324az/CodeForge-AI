package com.codeforge.ai.domain.prompt.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PromptTemplateSceneCatalog {

    private static final Map<String, SceneMeta> SCENES = new LinkedHashMap<>();

    static {
        register("CODE_GEN", "代码生成", "WEB_APP");
        register("API_GEN", "API 接口", "API_SERVICE");
        register("PAGE_GENERATION", "页面生成", "WEB_APP");
        register("APP_GENERATION", "应用生成", "WEB_APP");
        register("ADMIN_WEB", "后台管理", "ADMIN_WEB");
        register("CONTENT", "内容创作", "BLOG");
        register("EFFICIENCY", "效率工具", "WEB_APP");
        register("DASHBOARD", "数据看板", "ADMIN_WEB");
        register("ECOMMERCE", "电商", "ADMIN_WEB");
    }

    private PromptTemplateSceneCatalog() {
    }

    public static String labelOf(String templateScene) {
        if (templateScene == null || templateScene.isBlank()) {
            return "其他";
        }
        SceneMeta meta = SCENES.get(templateScene);
        return meta == null ? templateScene : meta.label();
    }

    public static String applicableAppTypeOf(String templateScene) {
        if (templateScene == null || templateScene.isBlank()) {
            return "WEB_APP";
        }
        SceneMeta meta = SCENES.get(templateScene);
        return meta == null ? "WEB_APP" : meta.applicableAppType();
    }

    private static void register(String scene, String label, String applicableAppType) {
        SCENES.put(scene, new SceneMeta(label, applicableAppType));
    }

    private record SceneMeta(String label, String applicableAppType) {
    }
}
