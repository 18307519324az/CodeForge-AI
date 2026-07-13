package com.codeforge.ai.api;

import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "codeforge.release-gates.enabled=false"
})
class AdminReleaseGateSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void releaseGateShouldBeDisabledByDefaultForAdmin() throws Exception {
        String adminToken = jwtTokenProvider.createAccessToken(
                new CurrentUser(1L, "admin", List.of("PLATFORM_ADMIN")));
        mockMvc.perform(get("/v1/admin/release-gates/prompt-runtime-binding")
                        .param("taskId", "1")
                        .header("Authorization", "Bearer " + adminToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void releaseGateShouldRejectNormalUser() throws Exception {
        String userToken = jwtTokenProvider.createAccessToken(
                new CurrentUser(2L, "user", List.of("USER")));
        mockMvc.perform(get("/v1/admin/release-gates/prompt-runtime-binding")
                        .param("taskId", "1")
                        .header("Authorization", "Bearer " + userToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void releaseGateShouldRejectAnonymous() throws Exception {
        mockMvc.perform(get("/v1/admin/release-gates/prompt-runtime-binding")
                        .param("taskId", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
