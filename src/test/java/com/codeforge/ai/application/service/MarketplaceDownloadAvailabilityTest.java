package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeforge.ai.domain.app.enums.PublicationDownloadAvailability;
import org.junit.jupiter.api.Test;

class MarketplaceDownloadAvailabilityTest {

    @Test
    void disabledWhenAllowDownloadFalse() {
        assertThat(PublicationDownloadAvailabilityDeriver.derive(false, "READY"))
                .isEqualTo(PublicationDownloadAvailability.DISABLED);
    }

    @Test
    void availableWhenReadyExport() {
        assertThat(PublicationDownloadAvailabilityDeriver.derive(true, "READY"))
                .isEqualTo(PublicationDownloadAvailability.AVAILABLE);
    }

    @Test
    void processingWhenExportProcessing() {
        assertThat(PublicationDownloadAvailabilityDeriver.derive(true, "PROCESSING"))
                .isEqualTo(PublicationDownloadAvailability.PROCESSING);
    }

    @Test
    void notReadyWhenNoExport() {
        assertThat(PublicationDownloadAvailabilityDeriver.derive(true, null))
                .isEqualTo(PublicationDownloadAvailability.NOT_READY);
    }

    @Test
    void failedWhenExportFailed() {
        assertThat(PublicationDownloadAvailabilityDeriver.derive(true, "FAILED"))
                .isEqualTo(PublicationDownloadAvailability.FAILED);
    }
}
