package com.codeforge.ai.application.service;

import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory registry for active streaming sessions.
 * <p>
 * Enforces concurrency limits:
 * <ul>
 *   <li>Each app can have at most 1 active streaming session</li>
 *   <li>Each user can have at most 2 active streaming sessions</li>
 * </ul>
 * <p>
 * Provides a {@link #cancel(Long)} mechanism that interrupts the underlying
 * {@link CompletableFuture} to release backend resources.
 */
@Component
public class StreamSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(StreamSessionRegistry.class);
    private static final int MAX_SESSIONS_PER_USER = 2;

    /** Auto-incrementing session ID generator. */
    private final AtomicLong sessionIdSeq = new AtomicLong(1);

    /** appId → sessionId (at most 1 per app). */
    private final ConcurrentMap<Long, Long> appSessions = new ConcurrentHashMap<>();

    /** userId → count of active sessions. */
    private final ConcurrentMap<Long, AtomicInteger> userCounts = new ConcurrentHashMap<>();

    /** sessionId → session metadata. */
    private final ConcurrentMap<Long, SessionInfo> sessions = new ConcurrentHashMap<>();

    /**
     * Try to create a new streaming session for the given app and user.
     *
     * @param appId  the app being generated for
     * @param userId the user who initiated the generation
     * @return the newly allocated session ID
     * @throws BusinessException with status 409 if limits are exceeded
     */
    public long tryCreate(Long appId, Long userId, String requirement) {
        // App-level limit: at most 1 session per app
        Long existing = appSessions.get(appId);
        if (existing != null) {
            // Double-check that the session is still alive
            if (sessions.containsKey(existing)) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT,
                        "当前 app 已有生成任务进行中");
            }
            // Stale entry — clean it up
            appSessions.remove(appId, existing);
        }

        // User-level limit: at most 2 sessions per user
        AtomicInteger counter = userCounts.computeIfAbsent(userId, k -> new AtomicInteger(0));
        int current;
        while (true) {
            current = counter.get();
            if (current >= MAX_SESSIONS_PER_USER) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT,
                        "当前用户生成任务过多（最多 " + MAX_SESSIONS_PER_USER + " 个）");
            }
            if (counter.compareAndSet(current, current + 1)) {
                break;
            }
        }

        long sessionId = sessionIdSeq.getAndIncrement();
        appSessions.put(appId, sessionId);
        sessions.put(sessionId, new SessionInfo(appId, userId, null, null, requirement));

        log.info("Stream session created: sessionId={}, appId={}, userId={}", sessionId, appId, userId);
        return sessionId;
    }

    /**
     * Register the async computation future for a session so it can be cancelled later.
     */
    public void registerFuture(Long sessionId, CompletableFuture<?> future) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) {
            sessions.put(sessionId, info.withModelFuture(future));
        }
    }

    /**
     * Cancel a streaming session: interrupts the HTTP request and releases all resources.
     *
     * @param sessionId the session to cancel
     * @return true if a session was actually cancelled, false if it didn't exist
     */
    public boolean cancel(Long sessionId) {
        SessionInfo info = sessions.get(sessionId);
        if (info == null) {
            log.warn("Cancel requested for unknown session: {}", sessionId);
            return false;
        }

        // Interrupt the model HTTP request
        if (info.modelFuture() != null && !info.modelFuture().isDone()) {
            boolean cancelled = info.modelFuture().cancel(true);
            log.info("Stream session cancelled: sessionId={}, appId={}, futureCancelled={}",
                    sessionId, info.appId(), cancelled);
        }

        cleanup(sessionId);
        return true;
    }

    /**
     * Remove session tracking for the given session ID.
     */
    public void cleanup(Long sessionId) {
        SessionInfo info = sessions.remove(sessionId);
        if (info != null) {
            appSessions.remove(info.appId(), sessionId);
            AtomicInteger counter = userCounts.get(info.userId());
            if (counter != null) {
                counter.decrementAndGet();
                if (counter.get() <= 0) {
                    userCounts.remove(info.userId(), counter);
                }
            }
            log.debug("Stream session cleaned up: sessionId={}", sessionId);
        }
    }

    /**
     * Get the session info (or null if not found).
     */
    public SessionInfo getSessionInfo(Long sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Get the task ID for a session, or null.
     */
    public Long getTaskId(Long sessionId) {
        SessionInfo info = sessions.get(sessionId);
        return info != null ? info.taskId() : null;
    }

    /**
     * Set the task ID for a session (called after task is persisted).
     */
    public void setTaskId(Long sessionId, Long taskId) {
        SessionInfo info = sessions.get(sessionId);
        if (info != null) {
            sessions.put(sessionId, info.withTaskId(taskId));
        }
    }

    // ── Value types ──

    /**
     * Immutable metadata for an active streaming session.
     */
    public record SessionInfo(
            Long appId,
            Long userId,
            Long taskId,
            CompletableFuture<?> modelFuture,
            String requirement
    ) {
        SessionInfo withTaskId(Long taskId) {
            return new SessionInfo(appId, userId, taskId, modelFuture, requirement);
        }

        SessionInfo withModelFuture(CompletableFuture<?> future) {
            return new SessionInfo(appId, userId, taskId, future, requirement);
        }
    }
}
