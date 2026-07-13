package com.codeforge.ai.application.service.repair;

import com.codeforge.ai.application.service.GeneratedArtifactRepairApplicationService;
import com.codeforge.ai.application.service.GeneratedArtifactRepairAuditService;
import com.codeforge.ai.infrastructure.persistence.mapper.AiAppEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AppVersionEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.AuditLogEntityMapper;
import com.codeforge.ai.infrastructure.persistence.mapper.GeneratedFileEntityMapper;
import com.codeforge.ai.shared.util.GeneratedArtifactPathSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
class RepairCommitMustRunInsideActualSpringTransactionTest {

    @Autowired
    private GeneratedArtifactRepairApplicationService repairApplicationService;

    @Autowired
    private GeneratedArtifactRepairCommitService repairCommitService;

    @Autowired
    private GeneratedArtifactRepairTransactionProbe transactionProbe;

    @Autowired
    private RepairIntegrationTestDataFactory dataFactory;

    @TempDir
    Path storageRoot;

    private RepairIntegrationTestDataFactory.RepairFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        transactionProbe.clear();
        ReflectionTestUtils.setField(repairApplicationService, "storageRoot", storageRoot);
        fixture = dataFactory.seedRepairFixture(storageRoot);
    }

    @Test
    void RepairCommitMustRunInsideActualSpringTransactionTest() {
        repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId());

        GeneratedArtifactRepairTransactionProbe.ProbeState state = transactionProbe.lastState();
        assertThat(state).isNotNull();
        assertThat(state.transactionActive())
                .as("commit must run inside an active Spring transaction")
                .isTrue();
        assertThat(state.synchronizationActive())
                .as("transaction synchronization must be active during commit")
                .isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("test thread must not leak an open transaction")
                .isFalse();
    }

    @Test
    void SelectForUpdateRunsInsideTransactionTest() {
        repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId());

        GeneratedArtifactRepairTransactionProbe.ProbeState state = transactionProbe.lastState();
        assertThat(state).isNotNull();
        assertThat(state.selectForUpdateActive()).isTrue();
        assertThat(state.transactionActive()).isTrue();
    }

    @Test
    void ApplicationServiceCallsSeparateTransactionalBeanTest() {
        repairApplicationService.repairArtifactVersion(
                dataFactory.ownerUser(), fixture.appId(), fixture.sourceVersionId());

        assertThat(transactionProbe.lastState()).isNotNull();
        assertThat(repairCommitService).isNotSameAs(repairApplicationService);
    }
}
