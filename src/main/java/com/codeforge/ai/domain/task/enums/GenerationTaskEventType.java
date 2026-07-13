package com.codeforge.ai.domain.task.enums;

public enum GenerationTaskEventType {
    TASK_CREATED,
    TASK_STARTED,
    PROMPT_RENDERED,
    MODEL_CALL_STARTED,
    MODEL_DELTA,
    MODEL_CALL_FINISHED,
    FILES_GENERATED,
    VERSION_CREATED,
    TASK_SUCCESS,
    TASK_FAILED,
    TASK_CANCELLED
}
