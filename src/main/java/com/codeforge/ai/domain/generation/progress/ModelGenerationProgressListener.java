package com.codeforge.ai.domain.generation.progress;

@FunctionalInterface
public interface ModelGenerationProgressListener {

    ModelGenerationProgressListener NOOP = progress -> { };

    void onProgress(ModelGenerationProgress progress);
}
