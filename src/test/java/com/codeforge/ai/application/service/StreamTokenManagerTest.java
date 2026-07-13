package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StreamTokenManager}.
 * <p>
 * Covers: token creation, single-use consumption, expiry, null/blank handling.
 */
class StreamTokenManagerTest {

    private StreamTokenManager manager;

    @BeforeEach
    void setUp() {
        manager = new StreamTokenManager();
    }

    @Test
    void shouldCreateToken() {
        String token = manager.createToken(1L, 100L, 10L);
        assertThat(token).isNotNull().isNotEmpty();
        assertThat(manager.exists(token)).isTrue();
    }

    @Test
    void shouldConsumeValidToken() {
        String token = manager.createToken(1L, 100L, 10L);
        StreamTokenManager.TokenInfo info = manager.consume(token);
        assertThat(info).isNotNull();
        assertThat(info.sessionId()).isEqualTo(1L);
        assertThat(info.taskId()).isEqualTo(100L);
        assertThat(info.appId()).isEqualTo(10L);
        assertThat(info.expiresAt()).isNotNull();
    }

    @Test
    void shouldNotAllowDoubleConsumption() {
        String token = manager.createToken(1L, 100L, 10L);
        StreamTokenManager.TokenInfo first = manager.consume(token);
        assertThat(first).isNotNull();

        StreamTokenManager.TokenInfo second = manager.consume(token);
        assertThat(second).isNull();
    }

    @Test
    void shouldReturnNullForUnknownToken() {
        StreamTokenManager.TokenInfo info = manager.consume("unknown-token");
        assertThat(info).isNull();
    }

    @Test
    void shouldReturnNullForNullToken() {
        StreamTokenManager.TokenInfo info = manager.consume(null);
        assertThat(info).isNull();
    }

    @Test
    void shouldReturnNullForBlankToken() {
        StreamTokenManager.TokenInfo info = manager.consume("  ");
        assertThat(info).isNull();
    }

    @Test
    void shouldNotExistAfterConsumption() {
        String token = manager.createToken(1L, 100L, 10L);
        manager.consume(token);
        assertThat(manager.exists(token)).isFalse();
    }
}
