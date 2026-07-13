package com.codeforge.ai.api;

import com.codeforge.ai.application.service.GenerationTaskApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies current generation-task SSE and task-creation endpoint auth.
 * <p>
 * In the current contract, both endpoints require JWT.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SseQueryTokenAuthTest {

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
    void sseStreamEndpointShouldRequireJwt() throws Exception {
        mockMvc.perform(get("/v1/generation-tasks/99999/stream")
                .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void taskCreateEndpointShouldRequireJwt() throws Exception {
        mockMvc.perform(post("/v1/generation-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "workspaceId": 1001,
                          "appId": 3001,
                          "taskType": "APP_GENERATION",
                          "requirement": "test"
                        }
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void taskCreateEndpointShouldRejectInvalidJwt() throws Exception {
        mockMvc.perform(post("/v1/generation-tasks")
                .header("Authorization", "Bearer invalid-token-here")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "workspaceId": 1001,
                          "appId": 3001,
                          "taskType": "APP_GENERATION",
                          "requirement": "test"
                        }
                        """))
                .andExpect(status().isUnauthorized());
    }
}
