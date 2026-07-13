package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.auth.LoginResponse;
import com.codeforge.ai.application.dto.user.CurrentUserResponse;
import com.codeforge.ai.application.service.AuthApplicationService;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthApplicationService authApplicationService;
    private PreviewAccessTokenService previewAccessTokenService;

    @BeforeEach
    void setUp() {
        authApplicationService = mock(AuthApplicationService.class);
        previewAccessTokenService = mock(PreviewAccessTokenService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        given(previewAccessTokenService.buildExpiredPreviewCookie()).willReturn(
                org.springframework.http.ResponseCookie.from(
                        PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, "")
                        .path("/api/v1/static-preview")
                        .maxAge(0)
                        .build()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authApplicationService, previewAccessTokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnUnifiedLoginResponse() throws Exception {
        CurrentUserResponse currentUserResponse = new CurrentUserResponse(
                1L,
                "tester",
                "Tester",
                null,
                "tester@example.com",
                null,
                "ACTIVE",
                LocalDateTime.of(2026, 6, 22, 12, 0),
                List.of("USER")
        );
        given(authApplicationService.login(any())).willReturn(new LoginResponse(
                "jwt-token",
                "Bearer",
                7200,
                currentUserResponse,
                List.of("USER")
        ));

        mockMvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "tester",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.user.account").value("tester"));
    }

    @Test
    void shouldValidateRegisterRequest() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "",
                                  "password": "123",
                                  "confirmPassword": "",
                                  "displayName": "",
                                  "email": "bad-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
