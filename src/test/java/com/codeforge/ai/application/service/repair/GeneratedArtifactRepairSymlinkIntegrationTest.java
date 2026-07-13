package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.application.service.GeneratedArtifactRepairApplicationService;
import com.codeforge.ai.shared.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
class GeneratedArtifactRepairSymlinkIntegrationTest {

    @Autowired
    private GeneratedArtifactRepairApplicationService repairApplicationService;

    @Autowired
    private RepairIntegrationTestDataFactory dataFactory;

    @TempDir
    Path storageRoot;

    @TempDir
    Path outsideDir;

    private RepairIntegrationTestDataFactory.RepairFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(repairApplicationService, "storageRoot", storageRoot);
        fixture = dataFactory.seedIsolatedRepairFixture(storageRoot);
    }

    @Test
    void SourceSymlinkEscapeIsRejectedTest() throws Exception {
        Assumptions.assumeTrue(symlinkSupported(), "local symlink test skipped: symlink not supported");

        Path outsideFile = outsideDir.resolve("secret.txt");
        Files.writeString(outsideFile, "outside-secret", StandardCharsets.UTF_8);
        Path sourceRoot = dataFactory.sourceVersionRoot(fixture);
        Path symlink = sourceRoot.resolve("escape-link.html");
        Files.createSymbolicLink(symlink, outsideFile);

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(BusinessException.class);
        assertThat(Files.readString(outsideFile)).isEqualTo("outside-secret");
        assertThat(dataFactory.countVersionsForApp(fixture.appId())).isZero();
    }

    @Test
    void TargetParentSymlinkIsRejectedTest() throws Exception {
        Assumptions.assumeTrue(symlinkSupported(), "local symlink test skipped: symlink not supported");

        Path sourceRoot = dataFactory.sourceVersionRoot(fixture);
        Path outsideDirLink = sourceRoot.resolve("linked-parent");
        Files.createSymbolicLink(outsideDirLink, outsideDir);
        Path nested = outsideDirLink.resolve("nested.html");
        Files.writeString(nested, "nested", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void SymlinkFailureCreatesNoVersionOrFilesTest() throws Exception {
        Assumptions.assumeTrue(symlinkSupported(), "local symlink test skipped: symlink not supported");

        Path outsideFile = outsideDir.resolve("secret.txt");
        Files.writeString(outsideFile, "outside-secret", StandardCharsets.UTF_8);
        Path sourceRoot = dataFactory.sourceVersionRoot(fixture);
        Files.createSymbolicLink(sourceRoot.resolve("escape-link.html"), outsideFile);

        assertThatThrownBy(() -> repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId()));

        assertThat(dataFactory.countVersionsForApp(fixture.appId())).isZero();
        assertThat(dataFactory.loadApp(fixture.appId()).getCurrentVersionId())
                .isEqualTo(fixture.sourceVersionId());
    }

    private boolean symlinkSupported() {
        try {
            Path link = storageRoot.resolve("probe-link");
            Path target = storageRoot.resolve("probe-target");
            Files.writeString(target, "x", StandardCharsets.UTF_8);
            Files.createSymbolicLink(link, target);
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
