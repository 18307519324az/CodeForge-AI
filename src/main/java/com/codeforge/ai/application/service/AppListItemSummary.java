package com.codeforge.ai.application.service;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AppListItemSummary {
    Integer currentVersionNo;
    String latestGenerationSource;
    Integer generatedFileCount;
    String latestExportStatus;
    String displayStatus;
    String publicationStatus;
    String publicationSlug;
    Long publicationId;
}
