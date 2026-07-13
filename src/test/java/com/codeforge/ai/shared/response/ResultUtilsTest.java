package com.codeforge.ai.shared.response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ResultUtilsTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldReuseRequestIdFromMdc() {
        MDC.put("requestId", "req_manual");

        ApiResponse<String> response = ResultUtils.success("ok");

        assertThat(response.getRequestId()).isEqualTo("req_manual");
        assertThat(response.getData()).isEqualTo("ok");
    }
}
