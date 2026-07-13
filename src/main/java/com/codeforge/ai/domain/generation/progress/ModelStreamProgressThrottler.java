package com.codeforge.ai.domain.generation.progress;

/**
 * Throttles progress emissions to avoid persisting one MODEL_DELTA per provider chunk.
 */
public final class ModelStreamProgressThrottler {

    public static final long TIME_THRESHOLD_MS = 750L;
    public static final long CHAR_THRESHOLD = 1024L;

    private final ModelGenerationProgressListener listener;
    private long lastEmittedReceivedChars;
    private long lastEmitAtMs;
    private boolean hasEmitted;

    public ModelStreamProgressThrottler(ModelGenerationProgressListener listener) {
        this.listener = listener != null ? listener : ModelGenerationProgressListener.NOOP;
    }

    public void onNonEmptyDelta(ModelStreamProgressState state) {
        ModelGenerationProgress snapshot = state.snapshot();
        if (snapshot.chunkCount() <= 0) {
            return;
        }
        if (!hasEmitted) {
            emit(snapshot);
            return;
        }
        long charsSinceLastEmit = snapshot.receivedChars() - lastEmittedReceivedChars;
        long elapsedSinceLastEmit = snapshot.elapsedMs() - lastEmitAtMs;
        if (charsSinceLastEmit >= CHAR_THRESHOLD || elapsedSinceLastEmit >= TIME_THRESHOLD_MS) {
            emit(snapshot);
        }
    }

    public void finalFlush(ModelStreamProgressState state) {
        ModelGenerationProgress snapshot = state.snapshot();
        if (snapshot.chunkCount() <= 0) {
            return;
        }
        if (!hasEmitted || snapshot.receivedChars() != lastEmittedReceivedChars) {
            emit(snapshot);
        }
    }

    public void resetForNewAttempt() {
        lastEmittedReceivedChars = 0L;
        lastEmitAtMs = 0L;
        hasEmitted = false;
    }

    private void emit(ModelGenerationProgress snapshot) {
        listener.onProgress(snapshot);
        lastEmittedReceivedChars = snapshot.receivedChars();
        lastEmitAtMs = snapshot.elapsedMs();
        hasEmitted = true;
    }
}
