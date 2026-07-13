package com.codeforge.ai.api;

import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.entity.UserRoleEntity;
import com.codeforge.ai.domain.auth.enums.PlatformRole;
import com.codeforge.ai.domain.auth.enums.UserStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberRole;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberStatus;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.UserRoleEntityMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "codeforge.build.previewdir=target/test-static-previews")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticPreviewAuthTest {

    private static final Long APP_ID = 1L;
    private static final Long OWNER_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long VERSION_ID = 1001L;
    private static final Long VERSION_ID_OTHER = 1002L;
    private static final Long VERSION_ID_FALLBACK = 1003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AppVersionEntityMapper appVersionEntityMapper;

    @Autowired
    private GeneratedFileEntityMapper generatedFileEntityMapper;

    @Autowired
    private UserEntityMapper userEntityMapper;

    @Autowired
    private UserRoleEntityMapper userRoleEntityMapper;

    @Autowired
    private WorkspaceMemberEntityMapper workspaceMemberEntityMapper;

    @Autowired
    private PreviewAccessTokenService previewAccessTokenService;

    @BeforeEach
    void setUp() throws Exception {
        ensureUserExists();
        ensureWorkspaceMembershipExists();
        ensureVersionExists(VERSION_ID, 1);
        ensureVersionExists(VERSION_ID_OTHER, 2);
        ensureVersionExists(VERSION_ID_FALLBACK, 3);
        preparePreviewFiles(VERSION_ID);
        preparePreviewFiles(VERSION_ID_OTHER);
        ensureGeneratedPreviewFilesExist(VERSION_ID_FALLBACK);
    }

    @Test
    void shouldIssuePreviewTokenForReadableVersion() throws Exception {
        mockMvc.perform(post("/v1/apps/{appId}/versions/{versionId}/preview-token", APP_ID, VERSION_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createUserToken(OWNER_USER_ID, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.previewUrl").value(org.hamcrest.Matchers.containsString("/api/v1/static-preview/" + VERSION_ID + "/index.html?previewToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME)));
    }

    @Test
    void shouldAllowPreviewWhenQueryTokenAndCookieMatch() throws Exception {
        String previewToken = previewAccessTokenService.createPreviewToken(
                new CurrentUser(OWNER_USER_ID, "admin", List.of("PLATFORM_ADMIN")), VERSION_ID);

        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_ID)
                        .queryParam("previewToken", previewToken)
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, previewToken)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(result -> assertThat(result.getResponse().getContentType()).contains(MediaType.TEXT_HTML_VALUE))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("/api/v1/static-preview/" + VERSION_ID + "/assets/app.js?previewToken="));
    }

    @Test
    void StaticPreviewReturnsRawHtmlNotJsonStringTest() throws Exception {
        String previewToken = previewAccessTokenService.createPreviewToken(
                new CurrentUser(OWNER_USER_ID, "admin", List.of("PLATFORM_ADMIN")), VERSION_ID);

        String body = mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_ID)
                        .queryParam("previewToken", previewToken)
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, previewToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body.trim().toLowerCase()).startsWith("<!doctype html>");
        assertThat(body.trim()).doesNotStartWith("{");
        assertThat(body.trim()).doesNotStartWith("\"<!DOCTYPE");
    }

    @Test
    void StaticPreviewUsesUtf8HtmlContentTypeTest() throws Exception {
        String previewToken = previewAccessTokenService.createPreviewToken(
                new CurrentUser(OWNER_USER_ID, "admin", List.of("PLATFORM_ADMIN")), VERSION_ID);

        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_ID)
                        .queryParam("previewToken", previewToken)
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, previewToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("charset=UTF-8")));
    }

    @Test
    void shouldFallbackToGeneratedFilesWhenPreviewBuildMissing() throws Exception {
        String previewToken = previewAccessTokenService.createPreviewToken(
                new CurrentUser(OWNER_USER_ID, "admin", List.of("PLATFORM_ADMIN")), VERSION_ID_FALLBACK);

        Path fallbackPreviewDir = Path.of("target/test-static-previews", String.valueOf(VERSION_ID_FALLBACK));
        if (Files.exists(fallbackPreviewDir)) {
            try (var paths = Files.walk(fallbackPreviewDir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }

        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_ID_FALLBACK)
                        .queryParam("previewToken", previewToken)
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, previewToken)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("fallback-preview-" + VERSION_ID_FALLBACK));
    }

    @Test
    void shouldRejectPreviewWithoutAnyToken() throws Exception {
        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPreviewWithoutCookieBinding() throws Exception {
        String previewToken = previewAccessTokenService.createPreviewToken(
                new CurrentUser(OWNER_USER_ID, "admin", List.of("PLATFORM_ADMIN")), VERSION_ID);

        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_ID)
                        .queryParam("previewToken", previewToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPreviewWithInvalidToken() throws Exception {
        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_ID)
                        .queryParam("previewToken", "bad-token")
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, "bad-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCrossUserPreviewTokenIssue() throws Exception {
        mockMvc.perform(post("/v1/apps/{appId}/versions/{versionId}/preview-token", APP_ID, VERSION_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + createUserToken(OTHER_USER_ID, "user-b")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectVersionMismatchPreviewAccess() throws Exception {
        String previewToken = previewAccessTokenService.createPreviewToken(
                new CurrentUser(OWNER_USER_ID, "admin", List.of("PLATFORM_ADMIN")), VERSION_ID);

        mockMvc.perform(get("/v1/static-preview/{versionId}/index.html", VERSION_ID_OTHER)
                        .queryParam("previewToken", previewToken)
                        .cookie(new MockCookie(PreviewAccessTokenService.PREVIEW_TOKEN_COOKIE_NAME, previewToken)))
                .andExpect(status().isForbidden());
    }

    private String createUserToken(Long userId, String account) {
        return jwtTokenProvider.createAccessToken(new CurrentUser(userId, account, List.of("USER")));
    }

    private void ensureUserExists() {
        if (userEntityMapper.selectOneById(OTHER_USER_ID) != null) {
            return;
        }
        UserEntity userEntity = UserEntity.builder()
                .id(OTHER_USER_ID)
                .account("user-b")
                .passwordHash("$2a$10$WZx9Gra4Q8EDRUtR2WCR/.PsGM824Qy4ZlFdoCDNOdoAxXTK3Y6he")
                .displayName("User B")
                .email("user-b@example.com")
                .status(UserStatus.ACTIVE.name())
                .build();
        userEntity.setCreatedBy(OWNER_USER_ID);
        userEntity.setUpdatedBy(OWNER_USER_ID);
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setUpdatedAt(LocalDateTime.now());
        userEntity.setIsDeleted(0);
        userEntityMapper.insert(userEntity);

        UserRoleEntity userRoleEntity = UserRoleEntity.builder()
                .userId(OTHER_USER_ID)
                .roleCode(PlatformRole.USER.name())
                .build();
        userRoleEntity.setCreatedBy(OWNER_USER_ID);
        userRoleEntity.setUpdatedBy(OWNER_USER_ID);
        userRoleEntity.setCreatedAt(LocalDateTime.now());
        userRoleEntity.setUpdatedAt(LocalDateTime.now());
        userRoleEntity.setIsDeleted(0);
        userRoleEntityMapper.insert(userRoleEntity);
    }

    private void ensureWorkspaceMembershipExists() {
        if (workspaceMemberEntityMapper.findByWorkspaceIdAndUserId(1L, OWNER_USER_ID) == null) {
            WorkspaceMemberEntity ownerMembership = WorkspaceMemberEntity.builder()
                    .workspaceId(1L)
                    .userId(OWNER_USER_ID)
                    .memberRole(WorkspaceMemberRole.OWNER.name())
                    .memberStatus(WorkspaceMemberStatus.ACTIVE.name())
                    .invitedBy(OWNER_USER_ID)
                    .joinedAt(LocalDateTime.now())
                    .build();
            ownerMembership.setCreatedBy(OWNER_USER_ID);
            ownerMembership.setUpdatedBy(OWNER_USER_ID);
            ownerMembership.setCreatedAt(LocalDateTime.now());
            ownerMembership.setUpdatedAt(LocalDateTime.now());
            ownerMembership.setIsDeleted(0);
            workspaceMemberEntityMapper.insertWorkspaceMember(ownerMembership);
        }
    }

    private void ensureVersionExists(Long versionId, int versionNo) {
        if (appVersionEntityMapper.findById(versionId) != null) {
            return;
        }
        AppVersionEntity versionEntity = AppVersionEntity.builder()
                .id(versionId)
                .appId(APP_ID)
                .versionNo(versionNo)
                .versionSource("RULE")
                .sourceTaskId(1L)
                .changeSummary("preview-auth-test")
                .status("ACTIVE")
                .build();
        versionEntity.setCreatedBy(OWNER_USER_ID);
        versionEntity.setUpdatedBy(OWNER_USER_ID);
        versionEntity.setCreatedAt(LocalDateTime.now());
        versionEntity.setUpdatedAt(LocalDateTime.now());
        versionEntity.setIsDeleted(0);
        appVersionEntityMapper.insert(versionEntity);
    }

    private void preparePreviewFiles(Long versionId) throws Exception {
        Path previewDir = Path.of("target/test-static-previews", String.valueOf(versionId));
        Files.createDirectories(previewDir.resolve("assets"));
        Files.writeString(previewDir.resolve("index.html"), """
                <!doctype html>
                <html>
                <head>
                  <link rel="stylesheet" href="/assets/app.css">
                </head>
                <body>
                  <div id="app">preview-%d</div>
                  <script type="module" src="/assets/app.js"></script>
                </body>
                </html>
                """.formatted(versionId), StandardCharsets.UTF_8);
        Files.writeString(previewDir.resolve("assets/app.css"), "body{background:url(/assets/bg.png);}", StandardCharsets.UTF_8);
        Files.writeString(previewDir.resolve("assets/app.js"), "console.log('/assets/chunk.js');", StandardCharsets.UTF_8);
        Files.write(previewDir.resolve("assets/bg.png"), new byte[] {1, 2, 3});
    }

    private void ensureGeneratedPreviewFilesExist(Long versionId) {
        insertGeneratedFileIfMissing(versionId, "index.html", "index.html",
                """
                <!doctype html>
                <html>
                <body>
                <div id="app">fallback-preview-%d</div>
                </body>
                </html>
                """.formatted(versionId));
    }

    private void insertGeneratedFileIfMissing(Long versionId, String filePath, String fileName, String fileContent) {
        if (generatedFileEntityMapper.findByAppVersionIdAndFilePath(versionId, filePath) != null) {
            return;
        }
        GeneratedFileEntity fileEntity = GeneratedFileEntity.builder()
                .appVersionId(versionId)
                .filePath(filePath)
                .fileName(fileName)
                .fileType("html")
                .storagePath("target/generated-preview-tests/" + versionId + "/" + fileName)
                .fileContent(fileContent)
                .fileSize((long) fileContent.length())
                .build();
        fileEntity.setCreatedBy(OWNER_USER_ID);
        fileEntity.setUpdatedBy(OWNER_USER_ID);
        fileEntity.setCreatedAt(LocalDateTime.now());
        fileEntity.setUpdatedAt(LocalDateTime.now());
        fileEntity.setIsDeleted(0);
        generatedFileEntityMapper.insertFile(fileEntity);
    }
}
