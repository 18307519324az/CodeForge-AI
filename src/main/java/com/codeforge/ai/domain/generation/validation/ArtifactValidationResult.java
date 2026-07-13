package com.codeforge.ai.domain.generation.validation;

import java.util.List;

public record ArtifactValidationResult(
        boolean passed,
        String errorCode,
        List<String> issues) {

    public static ArtifactValidationResult valid() {
        return new ArtifactValidationResult(true, null, List.of());
    }

    public static ArtifactValidationResult invalid(String errorCode, List<String> issues) {
        return new ArtifactValidationResult(false, errorCode, List.copyOf(issues));
    }

    public boolean isValid() {
        return passed;
    }

    public String summary() {
        return issues.isEmpty() ? "unknown artifact issue" : String.join("; ", issues);
    }
}
