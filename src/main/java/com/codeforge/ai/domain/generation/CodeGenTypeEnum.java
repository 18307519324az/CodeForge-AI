package com.codeforge.ai.domain.generation;

public enum CodeGenTypeEnum {
    HTML,
    MULTI_FILE,
    VUE_PROJECT;

    public static CodeGenTypeEnum fromAppType(String appType) {
        if (appType == null) return MULTI_FILE;
        return switch (appType.toUpperCase()) {
            case "VUE_PROJECT", "VUE" -> VUE_PROJECT;
            case "HTML", "SINGLE_PAGE", "WEB_APP", "ADMIN_WEB" -> HTML;
            default -> MULTI_FILE;
        };
    }
}
