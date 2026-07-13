package com.codeforge.ai.application.dto.chat;

/**
 * Response returned when creating a new streaming chat session.
 */
public record ChatSessionResponse(
        Long sessionId,
        Long taskId,
        String streamToken,
        Integer expiresIn
) {}
