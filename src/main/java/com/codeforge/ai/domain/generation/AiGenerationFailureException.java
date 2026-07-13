package com.codeforge.ai.domain.generation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured AI generation failure with machine-readable task error code.
 */
public class AiGenerationFailureException extends RuntimeException {

    public static final String AI_OUTPUT_TRUNCATED = "AI_OUTPUT_TRUNCATED";
    public static final String AI_OUTPUT_INVALID_JSON = "AI_OUTPUT_INVALID_JSON";
    public static final String AI_OUTPUT_CONTRACT_INVALID = "AI_OUTPUT_CONTRACT_INVALID";
    public static final String AI_ARTIFACT_INVALID = "AI_ARTIFACT_INVALID";
    public static final String AI_ARTIFACT_ESCAPE_CORRUPTED = "AI_ARTIFACT_ESCAPE_CORRUPTED";
    public static final String AI_ARTIFACT_ENTRY_MISSING = "AI_ARTIFACT_ENTRY_MISSING";
    public static final String AI_ARTIFACT_ASSET_MISSING = "AI_ARTIFACT_ASSET_MISSING";

    private final String errorCode;
    private final String userMessage;
    private final Map<String, Object> metadata;

    public AiGenerationFailureException(String errorCode, String userMessage, String detailMessage) {
        this(errorCode, userMessage, detailMessage, Collections.emptyMap());
    }

    public AiGenerationFailureException(String errorCode,
                                      String userMessage,
                                      String detailMessage,
                                      Map<String, Object> metadata) {
        super(detailMessage != null && !detailMessage.isBlank() ? detailMessage : userMessage);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static AiGenerationFailureException truncated(Map<String, Object> metadata) {
        return new AiGenerationFailureException(
                AI_OUTPUT_TRUNCATED,
                "AI 输出超过长度限制，生成未完成",
                "Model output truncated after compact retry",
                metadata);
    }

    public static AiGenerationFailureException invalidJson(String detail, Map<String, Object> metadata) {
        return new AiGenerationFailureException(
                AI_OUTPUT_INVALID_JSON,
                "AI 输出 JSON 无效，无法解析",
                detail,
                metadata);
    }

    public static AiGenerationFailureException contractInvalid(String detail, Map<String, Object> metadata) {
        return new AiGenerationFailureException(
                AI_OUTPUT_CONTRACT_INVALID,
                "AI 输出不符合生成契约",
                detail,
                metadata);
    }

    public static AiGenerationFailureException artifactInvalid(String errorCode,
                                                               String userMessage,
                                                               String detail,
                                                               Map<String, Object> metadata) {
        return new AiGenerationFailureException(
                errorCode == null || errorCode.isBlank() ? AI_ARTIFACT_INVALID : errorCode,
                userMessage == null || userMessage.isBlank() ? "AI 生成产物无法运行" : userMessage,
                detail,
                metadata);
    }

    public String errorCode() {
        return errorCode;
    }

    public String userMessage() {
        return userMessage;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    public Map<String, Object> safeMetadata() {
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (value != null) {
                safe.put(key, value);
            }
        });
        return safe;
    }
}
