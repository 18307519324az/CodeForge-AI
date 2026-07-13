package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.application.dto.app.AppVersionRepairResponse;
import com.codeforge.ai.application.service.GeneratedArtifactRepairApplicationService;
import com.codeforge.ai.shared.util.GeneratedArtifactPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.Mockito.doThrow;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
class GeneratedArtifactRepairFilesystemCompensationTest {

    @Autowired
    private GeneratedArtifactRepairApplicationService repairApplicationService;

    @Autowired
    private RepairIntegrationTestDataFactory dataFactory;

    @SpyBean
    private com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper appVersionEntityMapper;

    @TempDir
    Path storageRoot;

    private RepairIntegrationTestDataFactory.RepairFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(appVersionEntityMapper);
        ReflectionTestUtils.setField(repairApplicationService, "storageRoot", storageRoot);
        fixture = dataFactory.seedIsolatedRepairFixture(storageRoot);
    }

    @Test
    void DatabaseFailureDeletesWrittenArtifactTest() throws Exception {
        doThrow(new RuntimeException("db down")).when(appVersionEntityMapper).insertVersion(any());
        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(RuntimeException.class);

        Path versionsDir = fixture.storageRoot()
                .resolve("apps")
                .resolve(String.valueOf(fixture.appId()))
                .resolve("versions");
        try (var children = Files.list(versionsDir)) {
            assertThat(children.map(path -> path.getFileName().toString()).toList())
                    .containsExactly(String.valueOf(fixture.sourceVersionId()));
        }
    }

    @Test
    void PartialWriteFailureDeletesStagingDirectoryTest() throws Exception {
        doThrow(new RuntimeException("db down")).when(appVersionEntityMapper).insertVersion(any());
        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()));

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

    @Test
    void SuccessfulCommitKeepsFinalDirectoryTest() {
        AppVersionRepairResponse response = repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId());
        Path finalDir = GeneratedArtifactPathSupport.resolveVersionRoot(
                fixture.storageRoot(), fixture.appId(), response.repairedVersionId());
        assertThat(Files.exists(finalDir.resolve("index.html"))).isTrue();
    }

    @Test
    void RollbackDoesNotDeleteHistoricalSourceVersionTest() throws Exception {
        Path sourceDir = dataFactory.sourceVersionRoot(fixture);
        Files.writeString(sourceDir.resolve("index.html"), "historical");
        doThrow(new RuntimeException("audit failed")).when(appVersionEntityMapper)
                .updatePreviewInfo(any(), any(), any(), any());
        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()));
        assertThat(Files.readString(sourceDir.resolve("index.html"))).isEqualTo("historical");
    }
}
