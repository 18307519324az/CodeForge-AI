package com.codeforge.ai.application.service.release;

public record TaskArtifactBindingResult(
        Long appId,
        Long appVersionId,
        String bindingSource,
        boolean resolved,
        String errorCode
) {
    public static final String ERROR_UNRESOLVED = "ARTIFACT_VERSION_UNRESOLVED";
    public static final String ERROR_CONFLICTING = "CONFLICTING_TASK_VERSION_IDS";
    public static final String ERROR_VERSION_NOT_FOUND = "ARTIFACT_VERSION_NOT_FOUND";
    public static final String ERROR_VERSION_APP_MISMATCH = "ARTIFACT_VERSION_APP_MISMATCH";

    public static final String SOURCE_VERSION_CREATED_EVENT = "VERSION_CREATED_EVENT";
    public static final String SOURCE_TASK_SUCCESS_EVENT = "TASK_SUCCESS_EVENT";
    public static final String SOURCE_SOURCE_TASK_VERSION = "SOURCE_TASK_VERSION";

    public static TaskArtifactBindingResult unresolved(Long appId, String errorCode) {
        return new TaskArtifactBindingResult(appId, null, null, false, errorCode);
    }

    public static TaskArtifactBindingResult resolved(Long appId, Long appVersionId, String bindingSource) {
        return new TaskArtifactBindingResult(appId, appVersionId, bindingSource, true, null);
    }
}
