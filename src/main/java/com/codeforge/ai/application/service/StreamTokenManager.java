package com.codeforge.ai.application.service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages short-lived, one-time stream tokens for SSE authentication.
 * <p>
 * Tokens are UUID-based, expire after 5 minutes, and can only be consumed once.
 * Token values are never logged — only session IDs are recorded in logs.
 */
@Component
public class StreamTokenManager {

    private static final Logger log = LoggerFactory.getLogger(StreamTokenManager.class);
    private static final long TOKEN_TTL_SECONDS = 300; // 5 minutes

    private final ConcurrentMap<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    /**
     * Create a new one-time stream token for the given session/task.
     *
     * @param sessionId the streaming session ID
     * @param taskId    the generation task ID
     * @param appId     the app ID
     * @return the raw token string (UUID)
     */
    public String createToken(Long sessionId, Long taskId, Long appId) {
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenInfo(sessionId, taskId, appId, Instant.now().plusSeconds(TOKEN_TTL_SECONDS)));
        // Never log the token value — only the sessionId
        log.info("Stream token created for session {} (expires in {}s)", sessionId, TOKEN_TTL_SECONDS);
        return token;
    }

    /**
     * Consume (validate and remove) a stream token.
     * <p>
     * A token can only be consumed once. After consumption, it is removed from the store
     * so that a replay attack with the same token will fail.
     *
     * @param token the raw token string to consume
     * @return the {@link TokenInfo} if valid, or {@code null} if the token is unknown or expired
     */
    public TokenInfo consume(String token) {
        if (token == null || token.isBlank()) return null;
        TokenInfo info = tokens.remove(token);
        if (info == null) {
            log.warn("Stream token consume failed: unknown token");
            return null;
        }
        if (info.expiresAt().isBefore(Instant.now())) {
            log.warn("Stream token expired: sessionId={}, expiresAt={}", info.sessionId(), info.expiresAt());
            return null;
        }
        log.info("Stream token consumed: sessionId={}", info.sessionId());
        return info;
    }

    /**
     * Check if a token exists without consuming it (for diagnostic use only).
     */
    public boolean exists(String token) {
        return token != null && tokens.containsKey(token);
    }

    // ── Value type ──

    /**
     * Metadata associated with a stream token.
     */
    public record TokenInfo(
            Long sessionId,
            Long taskId,
            Long appId,
            Instant expiresAt
    ) {}
}
