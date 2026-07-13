package com.codeforge.ai.application.dto.deploy;

import java.time.LocalDateTime;

public record DeploymentLogResponse(
        Long id,
        Long deploymentJobId,
        String logLevel,
        String logMessage,
        LocalDateTime logTime
) {
}
