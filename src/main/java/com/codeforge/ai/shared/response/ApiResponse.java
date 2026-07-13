package com.codeforge.ai.shared.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;

    private String message;

    private T data;

    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(0)
                .message("ok")
                .data(data)
                .requestId(ResultUtils.currentRequestId())
                .build();
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .requestId(ResultUtils.currentRequestId())
                .build();
    }
}
