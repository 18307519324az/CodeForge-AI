package com.codeforge.ai.domain.generation;

/**
 * A ModelGateway that supports streaming (SSE-based) chat responses.
 * <p>
 * Streaming gateways receive content incrementally via {@link ModelStreamHandler}
 * rather than returning a single complete response string.
 */
public interface StreamingModelGateway extends ModelGateway {

    /**
     * Whether this gateway implementation supports streaming for the given context.
     */
    boolean supportsStreaming();

    /**
     * Start a streaming chat session.
     * <p>
     * The implementation MUST call {@link ModelStreamHandler#onStart()} first,
     * then zero or more {@link ModelStreamHandler#onDelta(String)} calls,
     * then finally either {@link ModelStreamHandler#onComplete(ModelChatResult)}
     * or {@link ModelStreamHandler#onError(Throwable)}.
     *
     * @param context the generation context (requirement, app info, provider config)
     * @param handler the callback handler for streaming events
     */
    void streamChat(GenerationContext context, ModelStreamHandler handler);
}
