package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppDisplayStatusTest {

    @Test
    void shouldShowGeneratingWhenRunningTaskExists() {
        AiAppEntity app = AiAppEntity.builder().status("DEVELOPING").currentVersionId(9001L).build();
        assertThat(AppDisplayStatusDeriver.derive(app, true, 5, "SUCCESS", "NONE")).isEqualTo("GENERATING");
    }

    @Test
    void shouldShowReadyWhenCurrentVersionHasArtifacts() {
        AiAppEntity app = AiAppEntity.builder().status("DEVELOPING").currentVersionId(9001L).build();
        assertThat(AppDisplayStatusDeriver.derive(app, false, 5, "SUCCESS", "NONE")).isEqualTo("READY");
    }

    @Test
    void shouldShowPublishedWhenPublicationIsActive() {
        AiAppEntity app = AiAppEntity.builder().status("DEVELOPING").currentVersionId(9001L).build();
        assertThat(AppDisplayStatusDeriver.derive(app, false, 5, "SUCCESS", "PUBLISHED")).isEqualTo("PUBLISHED");
    }

    @Test
    void shouldShowFailedWhenNoVersionAndLatestTaskFailed() {
        AiAppEntity app = AiAppEntity.builder().status("DRAFT").currentVersionId(null).build();
        assertThat(AppDisplayStatusDeriver.derive(app, false, null, "FAILED", "NONE")).isEqualTo("FAILED");
    }

    @Test
    void shouldPreserveArchivedStatus() {
        AiAppEntity app = AiAppEntity.builder().status("ARCHIVED").currentVersionId(9001L).build();
        assertThat(AppDisplayStatusDeriver.derive(app, false, 5, "SUCCESS", "PUBLISHED")).isEqualTo("ARCHIVED");
    }

    @Test
    void shouldDefaultToDraft() {
        AiAppEntity app = AiAppEntity.builder().status("DRAFT").currentVersionId(null).build();
        assertThat(AppDisplayStatusDeriver.derive(app, false, null, null, "NONE")).isEqualTo("DRAFT");
    }
}
