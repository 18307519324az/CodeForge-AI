package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.codeforge.ai.application.dto.export.ExportPackageCreateRequest;
import com.codeforge.ai.domain.app.entity.AiAppEntity;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.domain.app.enums.ExportPackageType;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.ExportPackageEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.infrastructure.security.CurrentUser;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import com.codeforge.ai.shared.util.ExportPackagePathSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class ExportPackagePathBoundaryTest {

    private static final Long APP_ID = 3001L;
    private static final Long VERSION_ID = 88L;
    private static final Long WORKSPACE_ID = 1001L;

    @TempDir
    Path tempDir;

    @TempDir
    Path outsideDir;

    private ExportPackageApplicationService exportPackageApplicationService;
    private GeneratedFileEntityMapper generatedFileEntityMapper;

    @BeforeEach
    void setUp() throws Exception {
        AiAppEntityMapper aiAppEntityMapper = mock(AiAppEntityMapper.class);
        AppVersionEntityMapper appVersionEntityMapper = mock(AppVersionEntityMapper.class);
        generatedFileEntityMapper = mock(GeneratedFileEntityMapper.class);
        ExportPackageEntityMapper exportPackageEntityMapper = mock(ExportPackageEntityMapper.class);
        WorkspaceAccessService workspaceAccessService = mock(WorkspaceAccessService.class);

        exportPackageApplicationService = new ExportPackageApplicationService(
                aiAppEntityMapper,
                appVersionEntityMapper,
                generatedFileEntityMapper,
                exportPackageEntityMapper,
                workspaceAccessService);
        ReflectionTestUtils.setField(exportPackageApplicationService, "exportRoot", tempDir.toString());

        given(aiAppEntityMapper.selectOneById(APP_ID)).willReturn(AiAppEntity.builder()
                .id(APP_ID)
                .workspaceId(WORKSPACE_ID)
                .build());
        given(appVersionEntityMapper.findByAppIdAndVersionId(APP_ID, VERSION_ID))
                .willReturn(AppVersionEntity.builder()
                        .id(VERSION_ID)
                        .appId(APP_ID)
                        .versionNo(1)
                        .build());
        stubGeneratedFiles();
        org.mockito.Mockito.doAnswer(invocation -> {
            com.codeforge.ai.domain.app.entity.ExportPackageEntity entity = invocation.getArgument(0);
            entity.setId(9001L);
            entity.setCreatedAt(LocalDateTime.of(2026, 7, 11, 12, 0));
            return 1;
        }).when(exportPackageEntityMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void PackageTypeRejectsParentTraversalTest() {
        assertRejectedWithNoExternalFile("../escape");
    }

    @Test
    void PackageTypeRejectsForwardSlashTest() {
        assertRejectedWithNoExternalFile("a/b");
    }

    @Test
    void PackageTypeRejectsBackslashTest() {
        assertRejectedWithNoExternalFile("a\\b");
    }

    @Test
    void PackageTypeRejectsAbsolutePathTest() {
        assertRejectedWithNoExternalFile("/absolute");
    }

    @Test
    void PackageTypeRejectsDriveLetterTest() {
        assertRejectedWithNoExternalFile("C:\\temp");
    }

    @Test
    void PackageTypeRejectsUncPathTest() {
        assertRejectedWithNoExternalFile("\\\\server\\share");
    }

    @Test
    void FinalPackagePathMustRemainInsideVersionRootTest() {
        Path exportRoot = ExportPackagePathSupport.resolveExportRoot(tempDir.toString());
        Path versionRoot = ExportPackagePathSupport.resolveVersionRoot(exportRoot, APP_ID, 1);
        Path packagePath = ExportPackagePathSupport.resolvePackagePath(
                exportRoot, APP_ID, 1, ExportPackageType.ZIP, LocalDateTime.of(2026, 7, 11, 12, 0));

        assertThat(packagePath.startsWith(versionRoot)).isTrue();
        assertThat(packagePath.startsWith(exportRoot)).isTrue();
    }

    @Test
    void AllowedPackageTypeCreatesFileInsideVersionRootTest() throws Exception {
        long externalFilesBefore = countFiles(outsideDir);

        var response = exportPackageApplicationService.createExportPackage(
                new CurrentUser(8L, "amns", List.of("USER")),
                createRequest("ZIP"));

        Path exportRoot = ExportPackagePathSupport.resolveExportRoot(tempDir.toString());
        Path versionRoot = ExportPackagePathSupport.resolveVersionRoot(exportRoot, APP_ID, 1);
        Path createdPath = versionRoot.resolve(response.fileName()).normalize();

        assertThat(createdPath.startsWith(versionRoot)).isTrue();
        assertThat(Files.exists(createdPath)).isTrue();
        assertThat(countFiles(outsideDir)).isEqualTo(externalFilesBefore);
    }

    @Test
    void TraversalRequestCreatesNoExternalFileTest() {
        long externalFilesBefore = countFiles(outsideDir);
        assertRejectedWithNoExternalFile("..\\escape");
        assertThat(countFiles(outsideDir)).isEqualTo(externalFilesBefore);
        assertThat(countFiles(tempDir.resolve("apps"))).isZero();
    }

    private void assertRejectedWithNoExternalFile(String packageType) {
        long externalBefore = countFiles(outsideDir);
        assertThatThrownBy(() -> exportPackageApplicationService.createExportPackage(
                new CurrentUser(8L, "amns", List.of("USER")),
                createRequest(packageType)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
        assertThat(countFiles(outsideDir)).isEqualTo(externalBefore);
    }

    private ExportPackageCreateRequest createRequest(String packageType) {
        ExportPackageCreateRequest request = new ExportPackageCreateRequest();
        request.setAppId(APP_ID);
        request.setAppVersionId(VERSION_ID);
        request.setPackageType(packageType);
        return request;
    }

    private void stubGeneratedFiles() throws Exception {
        Path generatedFile = tempDir.resolve("generated").resolve("index.html");
        Files.createDirectories(generatedFile.getParent());
        Files.writeString(generatedFile, "<html>ok</html>", StandardCharsets.UTF_8);
        given(generatedFileEntityMapper.findByAppVersionId(VERSION_ID)).willReturn(List.of(
                GeneratedFileEntity.builder()
                        .appVersionId(VERSION_ID)
                        .filePath("index.html")
                        .fileName("index.html")
                        .fileType("html")
                        .storagePath(generatedFile.toString())
                        .build()));
    }

    private static long countFiles(Path root) {
        if (!Files.exists(root)) {
            return 0L;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        } catch (Exception exception) {
            return 0L;
        }
    }
}
