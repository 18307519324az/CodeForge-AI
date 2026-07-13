package com.codeforge.ai.application.dto.admin;

import java.util.List;

public record AiRoutingConfigResponse(
        String mode,
        String pinnedProviderCode,
        List<String> effectiveCandidates,
        boolean adminPersisted
) {
}
