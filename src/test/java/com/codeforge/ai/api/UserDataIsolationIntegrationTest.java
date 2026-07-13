package com.codeforge.ai.api;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.entity.UserRoleEntity;
import com.codeforge.ai.domain.auth.enums.PlatformRole;
import com.codeforge.ai.domain.auth.enums.UserStatus;
import com.codeforge.ai.domain.task.entity.GenerationTaskEntity;
import com.codeforge.ai.domain.task.enums.GenerationTaskStatus;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberRole;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberStatus;
import com.codeforge.ai.domain.workspace.enums.WorkspaceStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GenerationTaskEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserRoleEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.WorkspaceMemberEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.infrastructure.security.JwtTokenProvider;
import com.codeforge.ai.infrastructure.security.PreviewAccessTokenService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "codeforge.build.previewdir=target/test-isolation-previews",
        "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserDataIsolationIntegrationTest {

    private static final String USER_A_ACCOUNT = "isolation-user-a";
    private static final String USER_B_ACCOUNT = "isolation-user-b";
    private static final Long ADMIN_USER_ID = 1L;
    private static final Long USER_A_ID = 10L;
    private static final Long USER_B_ID = 11L;
    private static final Long WORKSPACE_A_ID = 101L;
    private static final Long WORKSPACE_B_ID = 102L;
    private static final Long APP_A_ID = 101L;
    private static final Long APP_B_ID = 102L;
    private static final Long TASK_A_ID = 101L;
    private static final Long TASK_B_ID = 102L;
    private static final Long VERSION_A_ID = 101L;
    private static final Long VERSION_B_ID = 102L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PreviewAccessTokenService previewAccessTokenService;

    @Autowired
    private UserEntityMapper userEntityMapper;

    @Autowired
    private UserRoleEntityMapper userRoleEntityMapper;

    @Autowired
    private WorkspaceEntityMapper workspaceEntityMapper;

    @Autowired
    private WorkspaceMemberEntityMapper workspaceMemberEntityMapper;

    @Autowired
    private AiAppEntityMapper aiAppEntityMapper;

    @Autowired
    private AppVersionEntityMapper appVersionEntityMapper;

    @Autowired
    private GenerationTaskEntityMapper generationTaskEntityMapper;

    @BeforeEach
    void setUp() throws Exception {
        ensureUser(USER_A_ID, USER_A_ACCOUNT, "User A", "isolation-user-a@example.com");
        ensureUser(USER_B_ID, USER_B_ACCOUNT, "User B", "isolation-user-b@example.com");
        ensureUserRole(USER_A_ID);
        ensureUserRole(USER_B_ID);
        ensureWorkspace(WORKSPACE_A_ID, "Workspace A", USER_A_ID);
        ensureWorkspace(WORKSPACE_B_ID, "Workspace B", USER_B_ID);
        ensureMembership(WORKSPACE_A_ID, USER_A_ID);
        ensureMembership(WORKSPACE_B_ID, USER_B_ID);
        ensureApp(APP_A_ID, WORKSPACE_A_ID, "App A", USER_A_ID);
        ensureApp(APP_B_ID, WORKSPACE_B_ID, "App B", USER_B_ID);
        ensureTask(TASK_A_ID, WORKSPACE_A_ID, APP_A_ID, USER_A_ID);
        ensureTask(TASK_B_ID, WORKSPACE_B_ID, APP_B_ID, USER_B_ID);
        ensureVersion(VERSION_A_ID, APP_A_ID, 1, TASK_A_ID, USER_A_ID);
        ensureVersion(VERSION_B_ID, APP_B_ID, 1, TASK_B_ID, USER_B_ID);
        preparePreviewFiles(VERSION_A_ID);
        preparePreviewFiles(VERSION_B_ID);
    }

    // --- App isolation ---

    @Test
    void userAAppsListShouldOnlyContainAppA() throws Exception {
        mockMvc.perform(get("/v1/apps")
                        .param("pageNo", "1")
                        .param("pageSize", "50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + APP_A_ID + ")]").exists())
                .andExpect(jsonPath("$.data.records[?(@.id == " + APP_B_ID + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id == 1)]").doesNotExist());
    }

    @Test
    void userBAppsListShouldOnlyContainAppB() throws Exception {
        mockMvc.perform(get("/v1/apps")
                        .param("pageNo", "1")
                        .param("pageSize", "50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + APP_B_ID + ")]").exists())
                .andExpect(jsonPath("$.data.records[?(@.id == " + APP_A_ID + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id == 1)]").doesNotExist());
    }

    @Test
    void userAShouldNotAccessAppBDetail() throws Exception {
        mockMvc.perform(get("/v1/apps/{appId}", APP_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userBShouldNotAccessAppADetail() throws Exception {
        mockMvc.perform(get("/v1/apps/{appId}", APP_A_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userAShouldNotCreateTaskForAppB() throws Exception {
        mockMvc.perform(post("/v1/generation-tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": %d,
                                  "appId": %d,
                                  "taskType": "RULE_GENERATE",
                                  "requirement": "cross access attempt"
                                }
                                """.formatted(WORKSPACE_B_ID, APP_B_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userBShouldNotCreateTaskForAppA() throws Exception {
        mockMvc.perform(post("/v1/generation-tasks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceId": %d,
                                  "appId": %d,
                                  "taskType": "RULE_GENERATE",
                                  "requirement": "cross access attempt"
                                }
                                """.formatted(WORKSPACE_A_ID, APP_A_ID)))
                .andExpect(status().isForbidden());
    }

    // --- Workspace isolation ---

    @Test
    void userAWorkspacesListShouldOnlyContainWorkspaceA() throws Exception {
        mockMvc.perform(get("/v1/workspaces")
                        .param("pageNo", "1")
                        .param("pageSize", "50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + WORKSPACE_A_ID + ")]").exists())
                .andExpect(jsonPath("$.data.records[?(@.id == " + WORKSPACE_B_ID + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id == 1)]").doesNotExist());
    }

    @Test
    void userBWorkspacesListShouldOnlyContainWorkspaceB() throws Exception {
        mockMvc.perform(get("/v1/workspaces")
                        .param("pageNo", "1")
                        .param("pageSize", "50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == " + WORKSPACE_B_ID + ")]").exists())
                .andExpect(jsonPath("$.data.records[?(@.id == " + WORKSPACE_A_ID + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id == 1)]").doesNotExist());
    }

    @Test
    void userAShouldNotAccessWorkspaceBDetail() throws Exception {
        mockMvc.perform(get("/v1/workspaces/{workspaceId}", WORKSPACE_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userBShouldNotAccessWorkspaceADetail() throws Exception {
        mockMvc.perform(get("/v1/workspaces/{workspaceId}", WORKSPACE_A_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    // --- Generation task isolation ---

    @Test
    void userAShouldReadOwnTask() throws Exception {
        mockMvc.perform(get("/v1/generation-tasks/{taskId}", TASK_A_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TASK_A_ID));
    }

    @Test
    void userBShouldReadOwnTask() throws Exception {
        mockMvc.perform(get("/v1/generation-tasks/{taskId}", TASK_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(TASK_B_ID));
    }

    @Test
    void userAShouldNotReadUserBTask() throws Exception {
        mockMvc.perform(get("/v1/generation-tasks/{taskId}", TASK_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userBShouldNotReadUserATask() throws Exception {
        mockMvc.perform(get("/v1/generation-tasks/{taskId}", TASK_A_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userAShouldNotOpenUserBTaskStream() throws Exception {
        mockMvc.perform(get("/v1/generation-tasks/{taskId}/stream", TASK_B_ID)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userBShouldNotOpenUserATaskStream() throws Exception {
        mockMvc.perform(get("/v1/generation-tasks/{taskId}/stream", TASK_A_ID)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    // --- Static preview isolation ---

    @Test
    void userAShouldIssuePreviewTokenForOwnVersion() throws Exception {
        mockMvc.perform(post("/v1/apps/{appId}/versions/{versionId}/preview-token", APP_A_ID, VERSION_A_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewUrl").value(
                        org.hamcrest.Matchers.containsString("/api/v1/static-preview/" + VERSION_A_ID + "/index.html?previewToken=")));
    }

    @Test
    void userBShouldNotIssuePreviewTokenForUserAVersion() throws Exception {
        mockMvc.perform(post("/v1/apps/{appId}/versions/{versionId}/preview-token", APP_A_ID, VERSION_A_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userAPreviewTokenShouldNotAccessUserBVersion() throws Exception {
        String previewToken = previewAccessTokenService.createPreviewToken(
                new CurrentUser(USER_A_ID, USER_A_ACCOUNT, List.of("USER")), VERSION_A_ID);

        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_B_ID)
                        .queryParam("previewToken", previewToken)
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, previewToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userBPreviewTokenShouldNotAccessUserAVersion() throws Exception {
        String previewToken = previewAccessTokenService.createPreviewToken(
                new CurrentUser(USER_B_ID, USER_B_ACCOUNT, List.of("USER")), VERSION_B_ID);

        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_A_ID)
                        .queryParam("previewToken", previewToken)
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, previewToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staticPreviewShouldRejectMissingToken() throws Exception {
        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_A_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staticPreviewShouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_A_ID)
                        .queryParam("previewToken", "bad-token")
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, "bad-token")))
                .andExpect(status().isUnauthorized());
    }

    // --- Admin isolation ---

    @Test
    void regularUserShouldNotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/v1/admin/users")
                        .param("pageNo", "1")
                        .param("pageSize", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void regularUserShouldNotAccessAdminModelCallLogs() throws Exception {
        mockMvc.perform(get("/v1/admin/model-call-logs")
                        .param("pageNo", "1")
                        .param("pageSize", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminShouldAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/v1/admin/users")
                        .param("pageNo", "1")
                        .param("pageSize", "10")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void userBShouldNotReadUserAFileContent() throws Exception {
        mockMvc.perform(get("/v1/apps/{appId}/versions/{versionId}/files/content", APP_A_ID, VERSION_A_ID)
                        .param("filePath", "index.html")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_B_ID, USER_B_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void UserACannotReadUserBPrivateAppTest() throws Exception {
        userAShouldNotAccessAppBDetail();
    }

    @Test
    void UserACannotReadUserBVersionsTest() throws Exception {
        mockMvc.perform(get("/v1/apps/{appId}/versions", APP_B_ID)
                        .param("pageNo", "1")
                        .param("pageSize", "20")
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void UserACannotReadUserBVersionDetailTest() throws Exception {
        mockMvc.perform(get("/v1/apps/{appId}/versions/{versionId}", APP_B_ID, VERSION_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void UserACannotReadUserBFilesTest() throws Exception {
        mockMvc.perform(get("/v1/apps/{appId}/versions/{versionId}/files", APP_B_ID, VERSION_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void UserACannotPreviewUserBPrivateVersionTest() throws Exception {
        mockMvc.perform(post("/v1/apps/{appId}/versions/{versionId}/preview-token", APP_B_ID, VERSION_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void UserACannotDownloadUserBPrivateVersionTest() throws Exception {
        mockMvc.perform(get("/v1/export-packages/{packageId}/download", 9999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void UserACannotModifyUserBAppTest() throws Exception {
        mockMvc.perform(put("/v1/apps/{appId}", APP_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hijacked App"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void UserACannotDeleteUserBAppTest() throws Exception {
        mockMvc.perform(delete("/v1/apps/{appId}", APP_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void UserACannotPublishUserBAppTest() throws Exception {
        mockMvc.perform(post("/v1/apps/{appId}/publications", APP_B_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionId": %d,
                                  "publicTitle": "Hijacked",
                                  "publicDescription": "cross publish",
                                  "slug": "hijacked-app",
                                  "allowPreview": true,
                                  "allowDownload": false
                                }
                                """.formatted(VERSION_B_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void UserACannotCreateVersionForUserBAppTest() throws Exception {
        userAShouldNotCreateTaskForAppB();
    }

    @Test
    void SnowflakeAuthorizationIdsRemainStringTest() throws Exception {
        mockMvc.perform(get("/v1/apps/{appId}", APP_A_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(USER_A_ID, USER_A_ACCOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString());
    }

    private String bearer(Long userId, String account) {
        return "Bearer " + jwtTokenProvider.createAccessToken(new CurrentUser(userId, account, List.of("USER")));
    }

    private String adminBearer() {
        return "Bearer " + jwtTokenProvider.createAccessToken(
                new CurrentUser(ADMIN_USER_ID, "admin", List.of("PLATFORM_ADMIN")));
    }

    private void ensureUser(Long userId, String account, String displayName, String email) {
        if (userEntityMapper.findById(userId) != null || userEntityMapper.findByAccount(account) != null) {
            return;
        }
        UserEntity userEntity = UserEntity.builder()
                .id(userId)
                .account(account)
                .passwordHash("$2a$10$WZx9Gra4Q8EDRUtR2WCR/.PsGM824Qy4ZlFdoCDNOdoAxXTK3Y6he")
                .displayName(displayName)
                .email(email)
                .status(UserStatus.ACTIVE.name())
                .build();
        userEntity.setCreatedBy(userId);
        userEntity.setUpdatedBy(userId);
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setUpdatedAt(LocalDateTime.now());
        userEntity.setIsDeleted(0);
        userEntityMapper.insert(userEntity);
    }

    private void ensureUserRole(Long userId) {
        if (userRoleEntityMapper.findByUserId(userId).stream()
                .anyMatch(role -> PlatformRole.USER.name().equals(role.getRoleCode()))) {
            return;
        }
        UserRoleEntity roleEntity = UserRoleEntity.builder()
                .userId(userId)
                .roleCode(PlatformRole.USER.name())
                .build();
        roleEntity.setCreatedBy(userId);
        roleEntity.setUpdatedBy(userId);
        roleEntity.setCreatedAt(LocalDateTime.now());
        roleEntity.setUpdatedAt(LocalDateTime.now());
        roleEntity.setIsDeleted(0);
        userRoleEntityMapper.insert(roleEntity);
    }

    private void ensureWorkspace(Long workspaceId, String name, Long ownerUserId) {
        if (workspaceEntityMapper.selectOneById(workspaceId) != null) {
            return;
        }
        WorkspaceEntity workspaceEntity = WorkspaceEntity.builder()
                .id(workspaceId)
                .name(name)
                .description("isolation test workspace")
                .ownerUserId(ownerUserId)
                .status(WorkspaceStatus.ACTIVE.name())
                .planCode("FREE")
                .build();
        workspaceEntity.setCreatedBy(ownerUserId);
        workspaceEntity.setUpdatedBy(ownerUserId);
        workspaceEntity.setCreatedAt(LocalDateTime.now());
        workspaceEntity.setUpdatedAt(LocalDateTime.now());
        workspaceEntity.setIsDeleted(0);
        workspaceEntityMapper.insert(workspaceEntity);
    }

    private void ensureMembership(Long workspaceId, Long userId) {
        if (workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(workspaceId, userId) != null) {
            return;
        }
        WorkspaceMemberEntity membership = WorkspaceMemberEntity.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .memberRole(WorkspaceMemberRole.OWNER.name())
                .memberStatus(WorkspaceMemberStatus.ACTIVE.name())
                .invitedBy(userId)
                .joinedAt(LocalDateTime.now())
                .build();
        membership.setCreatedBy(userId);
        membership.setUpdatedBy(userId);
        membership.setCreatedAt(LocalDateTime.now());
        membership.setUpdatedAt(LocalDateTime.now());
        membership.setIsDeleted(0);
        workspaceMemberEntityMapper.insertWorkspaceMember(membership);
    }

    private void ensureApp(Long appId, Long workspaceId, String name, Long ownerUserId) {
        if (aiAppEntityMapper.selectOneById(appId) != null) {
            return;
        }
        AiAppEntity appEntity = AiAppEntity.builder()
                .id(appId)
                .workspaceId(workspaceId)
                .name(name)
                .description("isolation test app")
                .appType("WEB_APP")
                .status("DRAFT")
                .visibility("PRIVATE")
                .build();
        appEntity.setCreatedBy(ownerUserId);
        appEntity.setUpdatedBy(ownerUserId);
        appEntity.setCreatedAt(LocalDateTime.now());
        appEntity.setUpdatedAt(LocalDateTime.now());
        appEntity.setIsDeleted(0);
        aiAppEntityMapper.insertApp(appEntity);
    }

    private void ensureTask(Long taskId, Long workspaceId, Long appId, Long ownerUserId) {
        if (generationTaskEntityMapper.selectOneById(taskId) != null) {
            return;
        }
        GenerationTaskEntity taskEntity = GenerationTaskEntity.builder()
                .id(taskId)
                .workspaceId(workspaceId)
                .appId(appId)
                .taskType("RULE_GENERATE")
                .taskStatus(GenerationTaskStatus.SUCCESS.name())
                .requirement("isolation test task")
                .retryCount(0)
                .queuedAt(LocalDateTime.now())
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .build();
        taskEntity.setCreatedBy(ownerUserId);
        taskEntity.setUpdatedBy(ownerUserId);
        taskEntity.setCreatedAt(LocalDateTime.now());
        taskEntity.setUpdatedAt(LocalDateTime.now());
        taskEntity.setIsDeleted(0);
        generationTaskEntityMapper.insert(taskEntity);
    }

    private void ensureVersion(Long versionId, Long appId, int versionNo, Long sourceTaskId, Long ownerUserId) {
        if (appVersionEntityMapper.findById(versionId) != null) {
            return;
        }
        AppVersionEntity versionEntity = AppVersionEntity.builder()
                .id(versionId)
                .appId(appId)
                .versionNo(versionNo)
                .versionSource("RULE")
                .sourceTaskId(sourceTaskId)
                .changeSummary("isolation test version")
                .status("ACTIVE")
                .build();
        versionEntity.setCreatedBy(ownerUserId);
        versionEntity.setUpdatedBy(ownerUserId);
        versionEntity.setCreatedAt(LocalDateTime.now());
        versionEntity.setUpdatedAt(LocalDateTime.now());
        versionEntity.setIsDeleted(0);
        appVersionEntityMapper.insert(versionEntity);
    }

    private void preparePreviewFiles(Long versionId) throws Exception {
        Path previewDir = Path.of("target/test-isolation-previews", String.valueOf(versionId));
        Files.createDirectories(previewDir);
        Files.writeString(previewDir.resolve("index.html"), """
                <!doctype html>
                <html><body><div id="app">isolation-preview-%d</div></body></html>
                """.formatted(versionId), StandardCharsets.UTF_8);
    }
}
