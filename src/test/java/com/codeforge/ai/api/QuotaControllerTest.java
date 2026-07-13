package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.quota.UserQuotaResponse;
import com.codeforge.ai.application.service.QuotaApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.web.RequestIdFilter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuotaControllerTest {

    private MockMvc mockMvc;
    private QuotaApplicationService quotaApplicationService;

    @BeforeEach
    void setUp() {
        quotaApplicationService = mock(QuotaApplicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new QuotaController(quotaApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnMyQuotaList() throws Exception {
        given(quotaApplicationService.getMyQuotas(any())).willReturn(List.of(
                new UserQuotaResponse(5001L, 2001L, 1001L, 10, 1000, new BigDecimal("12.50"),
                        "ACTIVE", null, null, LocalDateTime.of(2026, 6, 24, 0, 30))
        ));

        mockMvc.perform(get("/v1/quotas/me")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "user", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].workspaceId").value(1001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
