package com.codeforge.ai.domain.generation.progress;

/**
 * Tracks real streaming progress for one provider attempt.
 * {@code receivedChars} uses Java {@link String#length()} (UTF-16 code units).
 */
public final class ModelStreamProgressState {

    private final int attempt;
    private final long attemptStartedAtMs;
    private long receivedChars;
    private long chunkCount;

    public ModelStreamProgressState(int attempt) {
        this.attempt = attempt;
        this.attemptStartedAtMs = System.currentTimeMillis();
    }

    public int attempt() {
        return attempt;
    }

    public void recordNonEmptyDelta(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        receivedChars += delta.length();
        chunkCount++;
    }

    public void syncReceivedCharsFromFullContent(String fullContent) {
        if (fullContent != null) {
            receivedChars = fullContent.length();
        }
    }

    public ModelGenerationProgress snapshot() {
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - attemptStartedAtMs);
        return new ModelGenerationProgress(attempt, receivedChars, chunkCount, elapsedMs);
    }
}
