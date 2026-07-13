package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.workspace.WorkspaceMemberResponse;
import com.codeforge.ai.application.service.WorkspaceApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.web.RequestIdFilter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceControllerTest {

    private MockMvc mockMvc;
    private WorkspaceApplicationService workspaceApplicationService;

    @BeforeEach
    void setUp() {
        workspaceApplicationService = mock(WorkspaceApplicationService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(workspaceApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnWorkspaceMembers() throws Exception {
        given(workspaceApplicationService.listMembers(any(), any())).willReturn(List.of(
                new WorkspaceMemberResponse(
                        5001L,
                        3001L,
                        "editor",
                        "Editor User",
                        "EDITOR",
                        "ACTIVE",
                        LocalDateTime.of(2026, 6, 25, 10, 0)
                )
        ));

        mockMvc.perform(get("/v1/workspaces/1001/members")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "owner", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].memberId").value(5001))
                .andExpect(jsonPath("$.data[0].account").value("editor"));
    }

    @Test
    void shouldValidateAddWorkspaceMemberRequest() throws Exception {
        mockMvc.perform(post("/v1/workspaces/1001/members")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "owner", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "memberRole": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldUpdateWorkspace() throws Exception {
        given(workspaceApplicationService.updateWorkspace(any(), any(), any())).willReturn(
                new com.codeforge.ai.application.dto.workspace.WorkspaceDetailResponse(
                        1001L,
                        "Workspace A Updated",
                        "updated description",
                        2001L,
                        "ACTIVE",
                        "FREE",
                        "OWNER"
                )
        );

        mockMvc.perform(put("/v1/workspaces/1001")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "owner", List.of("USER")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Workspace A Updated",
                                  "description": "updated description"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.name").value("Workspace A Updated"))
                .andExpect(jsonPath("$.data.description").value("updated description"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
