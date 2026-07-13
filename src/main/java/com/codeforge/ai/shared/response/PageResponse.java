package com.codeforge.ai.shared.response;

import java.util.List;
import lombok.Builder;

@Builder
public record PageResponse<T>(
        List<T> records,
        long pageNo,
        long pageSize,
        long total
) {
}
