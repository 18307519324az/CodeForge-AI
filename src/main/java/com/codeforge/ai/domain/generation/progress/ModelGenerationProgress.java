package com.codeforge.ai.domain.generation.progress;

/**
 * Safe, public-facing model generation progress snapshot for a single provider attempt.
 * Does not carry any model content.
 */
public record ModelGenerationProgress(
        int attempt,
        long receivedChars,
        long chunkCount,
        long elapsedMs
) {
}
