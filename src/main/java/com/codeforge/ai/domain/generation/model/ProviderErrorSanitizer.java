package com.codeforge.ai.domain.generation.model;

import java.io.IOException;

public final class ProviderErrorSanitizer {

    public static final String PUBLIC_STREAM_INTERRUPTED = "AI 服务连接中断，请稍后重试";
    public static final String PUBLIC_PROVIDER_UNAVAILABLE = "AI 服务暂时不可用，请稍后重试";
    public static final String PUBLIC_INTERNAL_ERROR = "生成过程中发生内部错误，请稍后重试";

    private ProviderErrorSanitizer() {
    }

    public static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "模型调用失败";
        }
        String sanitized = message
                .replaceAll("(?i)sk-[a-zA-Z0-9]{20,}", "***")
                .replaceAll("(?i)Bearer\\s+[a-zA-Z0-9_\\-.]{20,}", "Bearer ***")
                .replaceAll("(?i)(api[_-]?key\\s*[:=]\\s*)[^\\s,;]+", "$1***");
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    public static String toPublicMessage(Throwable failure) {
        if (failure == null) {
            return PUBLIC_PROVIDER_UNAVAILABLE;
        }
        if (isSseBrowserTransportFailure(failure)) {
            return PUBLIC_INTERNAL_ERROR;
        }
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IOException && !isSseBrowserTransportFailure(current)) {
                return PUBLIC_STREAM_INTERRUPTED;
            }
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                if (isProviderTransportFailure(message)) {
                    return PUBLIC_STREAM_INTERRUPTED;
                }
                if (containsInternalTransportDetail(message)) {
                    return PUBLIC_INTERNAL_ERROR;
                }
            }
            current = current.getCause();
        }
        String topMessage = failure.getMessage();
        return topMessage == null || topMessage.isBlank()
                ? PUBLIC_PROVIDER_UNAVAILABLE
                : toPublicMessage(topMessage);
    }

    private static boolean isSseBrowserTransportFailure(Throwable failure) {
        if (failure == null) {
            return false;
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("asynccontext")
                || normalized.contains("asynclistener")
                || normalized.contains("broken pipe")
                || normalized.contains("connection reset");
    }

    public static String toPublicMessage(String message) {
        if (message == null || message.isBlank()) {
            return PUBLIC_PROVIDER_UNAVAILABLE;
        }
        if (isProviderTransportFailure(message) || containsInternalTransportDetail(message)) {
            return isProviderTransportFailure(message)
                    ? PUBLIC_STREAM_INTERRUPTED
                    : PUBLIC_INTERNAL_ERROR;
        }
        String sanitized = sanitize(message);
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }

    public static boolean isProviderTransportFailure(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("i/o error")
                || normalized.contains("io error")
                || normalized.contains("broken pipe")
                || normalized.contains("connection reset")
                || normalized.contains("connection closed")
                || normalized.contains("stream closed")
                || normalized.contains("socketexception")
                || normalized.contains("eofexception")
                || normalized.contains("unexpected end of stream")
                || normalized.contains("premature eof")
                || normalized.contains("closed");
    }

    public static boolean isProviderTransportFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            if (current.getMessage() != null && isProviderTransportFailure(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static String buildProviderFailureSummary(int providerCount, String lastErrorMessage) {
        String publicDetail = toPublicMessage(lastErrorMessage);
        if (providerCount <= 1) {
            return "AI 模型调用失败：" + publicDetail;
        }
        return "所有 AI 模型供应商调用均失败，最后错误：" + publicDetail;
    }

    public record PublicTaskError(String errorCode, String errorMessage) {
    }

    public static PublicTaskError sanitizeStoredTaskError(String errorCode, String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return new PublicTaskError(errorCode, errorMessage);
        }
        String publicMessage = toPublicMessage(errorMessage);
        String publicCode = isProviderTransportFailure(errorMessage) ? "AI_STREAM_INTERRUPTED" : errorCode;
        return new PublicTaskError(publicCode, publicMessage);
    }

    static boolean containsInternalTransportDetail(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("asynccontext")
                || normalized.contains("asynclistener")
                || normalized.contains("org.apache.catalina")
                || normalized.contains("org.apache.tomcat")
                || normalized.contains("servlet");
    }
}
