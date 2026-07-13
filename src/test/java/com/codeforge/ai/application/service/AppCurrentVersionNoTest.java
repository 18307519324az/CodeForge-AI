package com.codeforge.ai.application.service;

import com.codeforge.ai.domain.app.entity.AiAppEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppCurrentVersionNoTest {

    @Test
    void shouldExposeVersionNoFromCurrentVersionNotEntityId() {
        AppListItemSummary summary = AppListItemSummary.builder()
                .currentVersionNo(45)
                .build();
        assertThat(summary.getCurrentVersionNo()).isEqualTo(45);
        assertThat(summary.getCurrentVersionNo()).isNotEqualTo(9001);
    }
}
