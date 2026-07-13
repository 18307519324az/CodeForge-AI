package com.codeforge.ai.domain.generation.progress;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelStreamProgressThrottlerTest {

    private List<ModelGenerationProgress> emissions;
    private ModelStreamProgressThrottler throttler;

    @BeforeEach
    void setUp() {
        emissions = new ArrayList<>();
        throttler = new ModelStreamProgressThrottler(emissions::add);
    }

    @Test
    void firstNonEmptyDeltaEmitsImmediately() {
        ModelStreamProgressState state = new ModelStreamProgressState(1);
        state.recordNonEmptyDelta("hello");

        throttler.onNonEmptyDelta(state);

        assertThat(emissions).hasSize(1);
        assertThat(emissions.getFirst().receivedChars()).isEqualTo(5L);
        assertThat(emissions.getFirst().chunkCount()).isEqualTo(1L);
    }

    @Test
    void charThresholdTriggersSecondEmit() {
        ModelStreamProgressState state = new ModelStreamProgressState(1);
        state.recordNonEmptyDelta("a".repeat(500));
        throttler.onNonEmptyDelta(state);
        state.recordNonEmptyDelta("b".repeat(1100));
        throttler.onNonEmptyDelta(state);

        assertThat(emissions).hasSize(2);
        assertThat(emissions.get(1).receivedChars()).isEqualTo(1600L);
    }

    @Test
    void finalFlushEmitsRemainingProgress() {
        ModelStreamProgressState state = new ModelStreamProgressState(1);
        state.recordNonEmptyDelta("a".repeat(500));
        throttler.onNonEmptyDelta(state);
        state.recordNonEmptyDelta("b".repeat(200));
        throttler.onNonEmptyDelta(state);

        state.syncReceivedCharsFromFullContent("a".repeat(500) + "b".repeat(233));
        throttler.finalFlush(state);

        assertThat(emissions).hasSize(2);
        assertThat(emissions.getLast().receivedChars()).isEqualTo(733L);
    }

    @Test
    void emptyDeltaDoesNotIncreaseChunkCount() {
        ModelStreamProgressState state = new ModelStreamProgressState(1);
        state.recordNonEmptyDelta("x");
        state.recordNonEmptyDelta("");
        state.recordNonEmptyDelta(null);

        assertThat(state.snapshot().chunkCount()).isEqualTo(1L);
        assertThat(state.snapshot().receivedChars()).isEqualTo(1L);
    }

    @Test
    void manyChunksStayBounded() {
        ModelStreamProgressState state = new ModelStreamProgressState(1);
        for (int index = 0; index < 500; index++) {
            state.recordNonEmptyDelta("x".repeat(50));
            throttler.onNonEmptyDelta(state);
        }
        throttler.finalFlush(state);

        assertThat(emissions.size()).isBetween(10, 100);
    }
}
