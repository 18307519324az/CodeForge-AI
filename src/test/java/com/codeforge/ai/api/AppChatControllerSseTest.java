package com.codeforge.ai.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.codeforge.ai.application.service.GenerationTaskApplicationService;
import com.codeforge.ai.infrastructure.security.JwtTokenProvider;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Tests for the current generation-task SSE endpoints.
 * <p>
 * Covers: stream endpoint content type and task creation validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppChatControllerSseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private GenerationTaskApplicationService generationTaskApplicationService;

    private String createToken() {
        var user = new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN"));
        return jwtTokenProvider.createAccessToken(user);
    }

    @Test
    void shouldReturnSseContentType() throws Exception {
        SseEmitter emitter = new SseEmitter();
        given(generationTaskApplicationService.openTaskStream(any(), eq(99999L), any()))
                .willReturn(emitter);
        String token = createToken();

        mockMvc.perform(MockMvcRequestBuilders
                .get("/v1/generation-tasks/99999/stream")
                .header("Authorization", "Bearer " + token)
                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
    }

    @Test
    void shouldRejectTaskCreateWithMissingRequiredFields() throws Exception {
        String token = createToken();
        mockMvc.perform(MockMvcRequestBuilders
                .post("/v1/generation-tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "workspaceId": 1001,
                          "appId": 3001,
                          "taskType": "APP_GENERATION"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectTaskCreateWithBlankFields() throws Exception {
        String token = createToken();
        mockMvc.perform(MockMvcRequestBuilders
                .post("/v1/generation-tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "workspaceId": 1001,
                          "appId": 3001,
                          "taskType": "",
                          "requirement": ""
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidJwtTaskStream() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get("/v1/generation-tasks/99999/stream")
                .header("Authorization", "Bearer invalid-token-here")
                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUnauthenticatedTaskStream() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                .get("/v1/generation-tasks/99999/stream")
                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }
}
