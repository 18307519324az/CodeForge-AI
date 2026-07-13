package com.codeforge.ai.application.service;

import java.io.IOException;

/**
 * Detects SSE / servlet transport failures that must not fail generation business tasks.
 */
public final class SseTransportFailures {

    private SseTransportFailures() {
    }

    public static boolean isTransportFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                String normalized = message.toLowerCase();
                if (normalized.contains("asynccontext")
                        || normalized.contains("asynclistener")
                        || normalized.contains("response is already committed")
                        || normalized.contains("unable to handle the spring security exception because the response is already committed")
                        || normalized.contains("broken pipe")
                        || normalized.contains("connection reset")) {
                    return true;
                }
            }
            String className = current.getClass().getName();
            if (className.contains("ClientAbortException")
                    || className.contains("AsyncDispatch")) {
                return true;
            }
            if (current instanceof IllegalStateException && message != null
                    && (message.contains("completed") || message.contains("closed"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
