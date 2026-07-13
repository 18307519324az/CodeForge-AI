package com.codeforge.ai.application.generation;

import com.codeforge.ai.application.service.SseTransportFailures;
import com.codeforge.ai.domain.generation.AiGenerationFailureException;
import com.codeforge.ai.domain.generation.model.ProviderErrorSanitizer;
import com.codeforge.ai.domain.generation.parser.AiGeneratedProjectParser.AiOutputParseException;
import com.codeforge.ai.domain.generation.model.NoAiProviderAvailableException;

/**
 * Explicit fallback policy for AI generation failures.
 */
public final class GenerationFallbackPolicy {

    private GenerationFallbackPolicy() {
    }

    public enum Resolution {
        COMPACT_RETRY,
        JSON_RETRY,
        TASK_FAILED,
        RULE_FALLBACK
    }

    public static boolean isSseTransportFailure(Throwable failure) {
        return SseTransportFailures.isTransportFailure(failure);
    }

    public static Resolution resolveForFailure(Throwable failure) {
        if (failure instanceof AiGenerationFailureException) {
            return Resolution.TASK_FAILED;
        }
        if (failure instanceof AiOutputParseException) {
            return Resolution.TASK_FAILED;
        }
        if (failure instanceof NoAiProviderAvailableException) {
            return Resolution.RULE_FALLBACK;
        }
        if (isProviderUnavailable(failure)) {
            return Resolution.RULE_FALLBACK;
        }
        return Resolution.TASK_FAILED;
    }

    public static boolean allowsRuleFallback(Throwable failure) {
        return resolveForFailure(failure) == Resolution.RULE_FALLBACK;
    }

    private static boolean isProviderUnavailable(Throwable failure) {
        if (!(failure instanceof RuntimeException)) {
            return false;
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("未配置可用的 AI 模型供应商")
                || message.contains("所有模型供应商调用均失败")
                || message.contains("没有可用的模型供应商");
    }

    public static String taskErrorCode(Throwable failure) {
        if (failure instanceof AiGenerationFailureException generationFailure) {
            return generationFailure.errorCode();
        }
        if (failure instanceof AiOutputParseException) {
            return AiGenerationFailureException.AI_OUTPUT_INVALID_JSON;
        }
        if (ProviderErrorSanitizer.isProviderTransportFailure(failure)) {
            return "AI_STREAM_INTERRUPTED";
        }
        return "GENERATION_ERROR";
    }

    public static String taskErrorMessage(Throwable failure) {
        if (isSseTransportFailure(failure)) {
            return "生成过程中发生内部通信异常，请稍后重试";
        }
        if (failure instanceof AiGenerationFailureException generationFailure) {
            return generationFailure.userMessage();
        }
        if (failure instanceof AiOutputParseException parseException) {
            String message = parseException.getMessage();
            return message == null || message.isBlank()
                    ? "AI 输出 JSON 无效，无法解析"
                    : message;
        }
        return ProviderErrorSanitizer.toPublicMessage(failure);
    }
}
