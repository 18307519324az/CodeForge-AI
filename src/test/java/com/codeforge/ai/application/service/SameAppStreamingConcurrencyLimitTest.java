package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.*;

import com.codeforge.ai.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for concurrency limits in {@link StreamSessionRegistry}.
 * <p>
 * Covers: per-app limit (1 session), per-user limit (2 sessions),
 * different apps can proceed concurrently, cleanup allows re-creation.
 */
class SameAppStreamingConcurrencyLimitTest {

    private StreamSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new StreamSessionRegistry();
    }

    @Test
    void shouldCreateSessionForNewApp() {
        long sessionId = registry.tryCreate(1L, 10L, "test requirement");
        assertThat(sessionId).isPositive();
        assertThat(registry.getSessionInfo(sessionId)).isNotNull();
    }

    @Test
    void shouldRejectSecondSessionForSameApp() {
        registry.tryCreate(1L, 10L, "req1");
        assertThatThrownBy(() -> registry.tryCreate(1L, 10L, "req2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有生成任务");
    }

    @Test
    void shouldAllowDifferentAppsConcurrently() {
        long s1 = registry.tryCreate(1L, 10L, "app1 req");
        long s2 = registry.tryCreate(2L, 10L, "app2 req");
        assertThat(s1).isNotEqualTo(s2);
        assertThat(registry.getSessionInfo(s1)).isNotNull();
        assertThat(registry.getSessionInfo(s2)).isNotNull();
    }

    @Test
    void shouldRejectThirdSessionForSameUser() {
        registry.tryCreate(1L, 10L, "app1");
        registry.tryCreate(2L, 10L, "app2");
        assertThatThrownBy(() -> registry.tryCreate(3L, 10L, "app3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生成任务过多");
    }

    @Test
    void shouldAllowNewSessionAfterCleanup() {
        long s1 = registry.tryCreate(1L, 10L, "req");
        registry.cleanup(s1);

        long s2 = registry.tryCreate(1L, 10L, "new req");
        assertThat(s2).isPositive();
        assertThat(s2).isNotEqualTo(s1);
    }

    @Test
    void shouldAllowNewSessionAfterCancel() {
        long s1 = registry.tryCreate(1L, 10L, "req");
        registry.cancel(s1);

        long s2 = registry.tryCreate(1L, 10L, "new req");
        assertThat(s2).isPositive();
    }

    @Test
    void shouldTrackTaskId() {
        long sessionId = registry.tryCreate(1L, 10L, "req");
        registry.setTaskId(sessionId, 999L);
        assertThat(registry.getTaskId(sessionId)).isEqualTo(999L);
    }

    @Test
    void shouldReturnNullForUnknownSessionInfo() {
        assertThat(registry.getSessionInfo(999L)).isNull();
    }

    @Test
    void shouldReturnNullForUnknownTaskId() {
        assertThat(registry.getTaskId(999L)).isNull();
    }

    @Test
    void cancelShouldReturnFalseForUnknownSession() {
        assertThat(registry.cancel(999L)).isFalse();
    }
}
