package com.codeforge.ai.api;

import com.codeforge.ai.application.dto.app.AppVersionDetailResponse;
import com.codeforge.ai.application.dto.app.AppVersionDiffFileResponse;
import com.codeforge.ai.application.dto.app.AppVersionDiffResponse;
import com.codeforge.ai.application.dto.app.AppVersionFileContentResponse;
import com.codeforge.ai.application.dto.app.AppVersionListItemResponse;
import com.codeforge.ai.application.dto.app.AppVersionRollbackResponse;
import com.codeforge.ai.application.service.AppVersionApplicationService;
import com.codeforge.ai.application.service.GeneratedArtifactRepairApplicationService;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.GlobalExceptionHandler;
import com.codeforge.ai.shared.response.PageResponse;
import com.codeforge.ai.shared.web.RequestIdFilter;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppVersionControllerTest {

    private MockMvc mockMvc;
    private AppVersionApplicationService appVersionApplicationService;

    @BeforeEach
    void setUp() {
        appVersionApplicationService = mock(AppVersionApplicationService.class);
        GeneratedArtifactRepairApplicationService repairService = mock(GeneratedArtifactRepairApplicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AppVersionController(appVersionApplicationService, repairService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldReturnVersionList() throws Exception {
        given(appVersionApplicationService.listVersions(any(), eq(3001L), any())).willReturn(PageResponse.<AppVersionListItemResponse>builder()
                .records(List.of(new AppVersionListItemResponse(7001L, 1, "WEB_APP", 9001L, "summary", "ACTIVE", null, LocalDateTime.of(2026, 6, 23, 10, 0))))
                .pageNo(1)
                .pageSize(20)
                .total(1)
                .build());

        mockMvc.perform(get("/v1/apps/3001/versions")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].id").value(7001))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void shouldRejectMalformedVersionIdPath() throws Exception {
        mockMvc.perform(get("/v1/apps/3001/versions/50.0/files")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数格式错误"));
    }

    @Test
    void shouldReturnVersionFileContent() throws Exception {
        given(appVersionApplicationService.getFileContent(any(), eq(3001L), eq(7001L), eq("apps/3001/versions/1/result.md")))
                .willReturn(new AppVersionFileContentResponse(7001L, "apps/3001/versions/1/result.md", "result.md", "markdown", "# demo"));

        mockMvc.perform(get("/v1/apps/3001/versions/7001/files/content")
                        .param("filePath", "apps/3001/versions/1/result.md")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("result.md"))
                .andExpect(jsonPath("$.data.content").value("# demo"));
    }

    @Test
    void shouldReturnVersionDetail() throws Exception {
        given(appVersionApplicationService.getVersion(any(), eq(3001L), eq(7001L)))
                .willReturn(new AppVersionDetailResponse(7001L, 3001L, 1, "WEB_APP", 9001L, "summary", "ACTIVE",
                        null, LocalDateTime.of(2026, 6, 23, 10, 0), LocalDateTime.of(2026, 6, 23, 10, 5)));

        mockMvc.perform(get("/v1/apps/3001/versions/7001")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.versionSource").value("WEB_APP"));
    }

    @Test
    void shouldReturnVersionDiff() throws Exception {
        given(appVersionApplicationService.diffVersions(any(), eq(3001L), eq(7001L), eq(7002L)))
                .willReturn(new AppVersionDiffResponse(
                        3001L,
                        7001L,
                        1,
                        "hash_v1",
                        7002L,
                        2,
                        "hash_v2",
                        List.of(new AppVersionDiffFileResponse("result.md", "MODIFIED", "old_hash", "new_hash", 10L, 12L))
                ));

        mockMvc.perform(get("/v1/apps/3001/versions/diff")
                        .param("fromVersionId", "7001")
                        .param("toVersionId", "7002")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fromVersionId").value(7001))
                .andExpect(jsonPath("$.data.changedFiles[0].changeType").value("MODIFIED"));
    }

    @Test
    void shouldRollbackVersion() throws Exception {
        given(appVersionApplicationService.rollbackVersion(any(), eq(3001L), eq(7002L)))
                .willReturn(new AppVersionRollbackResponse(3001L, 7002L, 2, "ROLLED_BACK"));

        mockMvc.perform(post("/v1/apps/3001/versions/7002/rollback")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new TestingAuthenticationToken(new CurrentUser(2001L, "editor", List.of("USER"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(7002))
                .andExpect(jsonPath("$.data.status").value("ROLLED_BACK"));
    }

    private static final class TestingAuthenticationToken
            extends org.springframework.security.authentication.UsernamePasswordAuthenticationToken {

        private TestingAuthenticationToken(CurrentUser principal) {
            super(principal, null, List.of());
        }
    }
}
