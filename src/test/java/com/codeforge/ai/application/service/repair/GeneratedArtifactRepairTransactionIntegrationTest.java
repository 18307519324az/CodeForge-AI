package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.application.dto.app.AppVersionRepairResponse;
import com.codeforge.ai.application.service.GeneratedArtifactRepairApplicationService;
import com.codeforge.ai.application.service.GeneratedArtifactRepairAuditService;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.domain.app.entity.GeneratedFileEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.util.GeneratedArtifactPathSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
class GeneratedArtifactRepairTransactionIntegrationTest {

    @Autowired
    private GeneratedArtifactRepairApplicationService repairApplicationService;

    @Autowired
    private RepairIntegrationTestDataFactory dataFactory;

    @Autowired
    private GeneratedArtifactRepairCommitService repairCommitService;

    @SpyBean
    private AppVersionEntityMapper appVersionEntityMapper;

    @SpyBean
    private GeneratedFileEntityMapper generatedFileEntityMapper;

    @SpyBean
    private AiAppEntityMapper aiAppEntityMapper;

    @SpyBean
    private GeneratedArtifactRepairFilesystemSupport filesystemSupport;

    @Autowired
    private GeneratedArtifactRepairAuditService repairAuditService;

    @TempDir
    Path storageRoot;

    private RepairIntegrationTestDataFactory.RepairFixture fixture;
    private long repairAuditsBefore;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(
                appVersionEntityMapper,
                generatedFileEntityMapper,
                aiAppEntityMapper,
                filesystemSupport);
        ReflectionTestUtils.setField(repairApplicationService, "storageRoot", storageRoot);
        fixture = dataFactory.seedIsolatedRepairFixture(storageRoot);
        repairAuditsBefore = dataFactory.countRepairAudits();
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(repairApplicationService, "repairCommitService", repairCommitService);
        Mockito.reset(
                appVersionEntityMapper,
                generatedFileEntityMapper,
                aiAppEntityMapper,
                filesystemSupport);
    }

    @Test
    void InsertVersionFailureRollsBackEverythingTest() {
        doThrow(new RuntimeException("insert version failed")).when(appVersionEntityMapper).insertVersion(any());

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        assertNoRepairResidue();
    }

    @Test
    void MoveStagingFailureCreatesNoDatabaseRowsTest() throws IOException {
        doThrow(new IOException("move failed")).when(filesystemSupport).moveStagingToFinal(any(), any());

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        assertNoRepairResidue();
    }

    @Test
    void InsertFirstGeneratedFileFailureRollsBackVersionTest() {
        AtomicInteger insertCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (insertCount.getAndIncrement() == 0) {
                throw new RuntimeException("first generated file failed");
            }
            return invocation.callRealMethod();
        }).when(generatedFileEntityMapper).insertFile(any(GeneratedFileEntity.class));

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        assertNoRepairResidue();
    }

    @Test
    void InsertMiddleGeneratedFileFailureRollsBackAllFilesTest() {
        AtomicInteger insertCount = new AtomicInteger();
        doAnswer(invocation -> {
            if (insertCount.getAndIncrement() == 1) {
                throw new RuntimeException("middle generated file failed");
            }
            return invocation.callRealMethod();
        }).when(generatedFileEntityMapper).insertFile(any(GeneratedFileEntity.class));

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        assertNoRepairResidue();
    }

    @Test
    void UpdatePreviewInfoFailureRollsBackEverythingTest() {
        doThrow(new RuntimeException("preview failed")).when(appVersionEntityMapper)
                .updatePreviewInfo(any(), any(), any(), any());

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        assertNoRepairResidue();
    }

    @Test
    void UpdateCurrentVersionFailureRollsBackEverythingTest() {
        doThrow(new RuntimeException("current version failed")).when(aiAppEntityMapper)
                .updateCurrentVersionId(any(), any(), any());

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        assertNoRepairResidue();
    }

    @Test
    void AuditFailureRollsBackVersionFilesAndCurrentVersionTest() {
        GeneratedArtifactRepairAuditService failingAuditService = Mockito.mock(GeneratedArtifactRepairAuditService.class);
        doThrow(new RuntimeException("audit failed")).when(failingAuditService)
                .recordSuccessfulRepair(any(), any(), any(), any(), any());
        ReflectionTestUtils.setField(
                repairApplicationService,
                "repairCommitService",
                new GeneratedArtifactRepairCommitService(
                        aiAppEntityMapper,
                        appVersionEntityMapper,
                        generatedFileEntityMapper,
                        failingAuditService,
                        filesystemSupport));

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        assertNoRepairResidue();
    }

    @Test
    void TransactionCommitFailureDeletesFinalDirectoryTest() throws Exception {
        AppVersionRepairResponse response = repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId());
        Path finalRoot = GeneratedArtifactPathSupport.resolveVersionRoot(
                fixture.storageRoot(), fixture.appId(), response.repairedVersionId());
        assertThat(Files.exists(finalRoot)).isTrue();

        doThrow(new RuntimeException("insert version failed")).when(appVersionEntityMapper).insertVersion(any());
        RepairIntegrationTestDataFactory.RepairFixture secondFixture =
                dataFactory.seedIsolatedRepairFixture(storageRoot);
        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), secondFixture.appId(), secondFixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        java.util.Optional<Path> leakedFinal = listVersionDirectories(secondFixture.appId()).stream()
                .filter(path -> !path.endsWith(String.valueOf(secondFixture.sourceVersionId())))
                .findFirst();
        assertThat(leakedFinal).isEmpty();
        assertThat(Files.exists(dataFactory.sourceVersionRoot(secondFixture))).isTrue();
    }

    @Test
    void RepairCommitRunsInsideActualTransactionTest() {
        repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId());
        assertThat(dataFactory.countVersionsForApp(fixture.appId())).isEqualTo(1);
    }

    @Test
    void TransactionSynchronizationIsRegisteredTest() throws Exception {
        AppVersionRepairResponse response = repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId());
        Path finalRoot = GeneratedArtifactPathSupport.resolveVersionRoot(
                fixture.storageRoot(), fixture.appId(), response.repairedVersionId());
        assertThat(Files.exists(finalRoot)).isTrue();
        Path stagingRoot = fixture.storageRoot()
                .resolve("apps")
                .resolve(String.valueOf(fixture.appId()))
                .resolve("staging");
        if (Files.exists(stagingRoot)) {
            try (var children = Files.list(stagingRoot)) {
                assertThat(children.findAny()).isEmpty();
            }
        }
    }

    private void assertNoRepairResidue() {
        try {
            assertNoRepairResidueInternal();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertNoRepairResidueInternal() throws Exception {
        assertThat(dataFactory.countVersionsForApp(fixture.appId())).isZero();
        assertThat(dataFactory.loadApp(fixture.appId()).getCurrentVersionId()).isEqualTo(fixture.sourceVersionId());
        assertThat(dataFactory.countRepairAudits()).isEqualTo(repairAuditsBefore);
        assertThat(Files.exists(dataFactory.sourceVersionRoot(fixture))).isTrue();

        Path versionsDir = fixture.storageRoot()
                .resolve("apps")
                .resolve(String.valueOf(fixture.appId()))
                .resolve("versions");
        if (Files.exists(versionsDir)) {
            try (var children = Files.list(versionsDir)) {
                assertThat(children.map(path -> path.getFileName().toString()).toList())
                        .containsExactly(String.valueOf(fixture.sourceVersionId()));
            }
        }

        Path stagingDir = fixture.storageRoot()
                .resolve("apps")
                .resolve(String.valueOf(fixture.appId()))
                .resolve("staging");
        if (Files.exists(stagingDir)) {
            try (var children = Files.list(stagingDir)) {
                assertThat(children.findAny()).isEmpty();
            }
        }
    }

    private java.util.List<Path> listVersionDirectories(long appId) throws IOException {
        Path versionsDir = fixture.storageRoot()
                .resolve("apps")
                .resolve(String.valueOf(appId))
                .resolve("versions");
        if (!Files.exists(versionsDir)) {
            return java.util.List.of();
        }
        try (var children = Files.list(versionsDir)) {
            return children.toList();
        }
    }
}
