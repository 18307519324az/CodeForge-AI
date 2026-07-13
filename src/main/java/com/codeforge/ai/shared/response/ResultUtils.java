package com.codeforge.ai.shared.response;

import com.codeforge.ai.shared.web.RequestIdConstants;
import java.util.UUID;
import org.slf4j.MDC;

public final class ResultUtils {

    private ResultUtils() {
    }

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data);
    }

    public static ApiResponse<Void> success() {
        return ApiResponse.success();
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return ApiResponse.failure(code, message);
    }

    public static String currentRequestId() {
        String requestId = MDC.get(RequestIdConstants.MDC_KEY);
        if (requestId == null || requestId.isBlank()) {
            requestId = generateRequestId();
            MDC.put(RequestIdConstants.MDC_KEY, requestId);
        }
        return requestId;
    }

    public static String generateRequestId() {
        return RequestIdConstants.REQUEST_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
