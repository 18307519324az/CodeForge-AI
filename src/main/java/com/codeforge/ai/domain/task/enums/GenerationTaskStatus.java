package com.codeforge.ai.domain.task.enums;

public enum GenerationTaskStatus {
    QUEUED,
    RUNNING,
    GENERATING,
    PERSISTING,
    SUCCESS,
    FAILED,
    CANCELLED
}
