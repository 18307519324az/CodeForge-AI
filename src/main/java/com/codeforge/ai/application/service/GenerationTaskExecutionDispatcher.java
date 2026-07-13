package com.codeforge.ai.application.service;

public interface GenerationTaskExecutionDispatcher {

    void scheduleTaskExecution(Long taskId, Long userId, String requestId);
}
