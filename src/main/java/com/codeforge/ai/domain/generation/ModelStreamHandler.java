package com.codeforge.ai.domain.generation;

import com.codeforge.ai.domain.generation.model.ModelChatResult;

/**
 * Handler for streaming model responses.
 * Callbacks are invoked in order: onStart → onDelta* → onComplete | onError.
 */
public interface ModelStreamHandler {

    /** Called once when streaming begins. */
    void onStart();

    /**
     * Called for each content delta received from the model.
     * @param delta a fragment of the model's response text (may be empty, should be filtered)
     */
    void onDelta(String delta);

    /** Called when an error occurs during streaming. */
    void onError(Throwable error);

    /**
     * Called when streaming completes successfully.
     * @param result the accumulated result including full content, provider, model, usage, latency
     */
    void onComplete(ModelChatResult result);
}
