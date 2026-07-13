package com.codeforge.ai.application.dto.admin;

public record PromptRuntimeBindingGateResponse(
        Long taskId,
        Long appId,
        Long appVersionId,
        String artifactBindingSource,
        boolean artifactVersionResolved,
        String artifactBindingErrorCode,
        Long modelCallId,
        String generationSource,
        String attemptPhase,
        Long pinnedTemplateVersionId,
        boolean matchesPinnedVersion,
        boolean matchesLatestVersion,
        boolean systemHashMatches,
        boolean userHashMatches,
        boolean combinedMatches,
        boolean v1SystemMatch,
        boolean v1UserMatch,
        boolean v1CombinedMatch,
        boolean v2SystemMatch,
        boolean v2UserMatch,
        boolean v2CombinedMatch
) {
}
