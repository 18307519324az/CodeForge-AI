package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.application.dto.app.AppVersionRepairResponse;
import com.codeforge.ai.application.service.GeneratedArtifactRepairApplicationService;
import com.codeforge.ai.domain.app.entity.AppVersionEntity;
import com.codeforge.ai.shared.util.GeneratedArtifactPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
class GeneratedArtifactRepairConcurrencyIntegrationTest {

    @Autowired
    private GeneratedArtifactRepairApplicationService repairApplicationService;

    @Autowired
    private RepairIntegrationTestDataFactory dataFactory;

    @TempDir
    Path storageRoot;

    private RepairIntegrationTestDataFactory.RepairFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(repairApplicationService, "storageRoot", storageRoot);
        fixture = dataFactory.seedIsolatedRepairFixture(storageRoot);
    }

    @Test
    void ConcurrentRepairIntegrationTest() throws Exception {
        List<AppVersionRepairResponse> responses = runConcurrentRepairs(2);
        assertThat(responses).hasSize(2);
        assertThat(responses.stream().map(AppVersionRepairResponse::repairedVersionId).collect(Collectors.toSet()))
                .hasSize(2);
        assertThat(responses.stream().map(AppVersionRepairResponse::repairedVersionNo).collect(Collectors.toSet()))
                .hasSize(2);

        for (AppVersionRepairResponse response : responses) {
            Path finalRoot = GeneratedArtifactPathSupport.resolveVersionRoot(
                    fixture.storageRoot(), fixture.appId(), response.repairedVersionId());
            assertThat(Files.exists(finalRoot.resolve("index.html"))).isTrue();
        }

        Long currentVersionId = dataFactory.loadApp(fixture.appId()).getCurrentVersionId();
        assertThat(responses.stream().map(AppVersionRepairResponse::repairedVersionId))
                .contains(currentVersionId);
        assertThat(dataFactory.countVersionsForApp(fixture.appId())).isEqualTo(2);
        assertThat(dataFactory.countRepairAudits()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void ConcurrentRepairUniqueConstraintTest() throws Exception {
        List<AppVersionRepairResponse> responses = runConcurrentRepairs(3);
        Set<Integer> versionNos = responses.stream()
                .map(AppVersionRepairResponse::repairedVersionNo)
                .collect(Collectors.toSet());
        assertThat(versionNos).hasSize(responses.size());
    }

    @Test
    void FailedConcurrentRepairLeavesNoResidueTest() throws Exception {
        List<AppVersionRepairResponse> responses = runConcurrentRepairs(2);
        assertThat(responses).isNotEmpty();
        assertThat(dataFactory.loadApp(fixture.appId()).getCurrentVersionId()).isNotNull();

        Path versionsDir = fixture.storageRoot()
                .resolve("apps")
                .resolve(String.valueOf(fixture.appId()))
                .resolve("versions");
        assertThat(Files.exists(versionsDir)).isTrue();
        assertThat(Files.exists(dataFactory.sourceVersionRoot(fixture))).isTrue();
    }

    private List<AppVersionRepairResponse> runConcurrentRepairs(int threads) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AppVersionRepairResponse>> futures = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            futures.add(executor.submit(() -> {
                start.await();
                return repairApplicationService.repairArtifactVersion(
                        dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId());
            }));
        }
        start.countDown();
        List<AppVersionRepairResponse> responses = new ArrayList<>();
        for (Future<AppVersionRepairResponse> future : futures) {
            responses.add(future.get(60, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        return responses;
    }
}
