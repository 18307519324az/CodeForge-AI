package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.application.generation.GenerationFallbackPolicy;
import com.codeforge.ai.domain.generation.progress.ModelStreamProgressState;
import com.codeforge.ai.domain.generation.progress.ModelStreamProgressThrottler;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderStreamFailureHandlingTest {

    @Test
    void providerStreamCloseAfterPartialDeltaDoesNotCorruptNextAttemptTest() {
        ModelStreamProgressThrottler throttler = new ModelStreamProgressThrottler(progress -> { });
        ModelStreamProgressState firstAttempt = new ModelStreamProgressState(1);
        firstAttempt.recordNonEmptyDelta("partial-output");
        throttler.onNonEmptyDelta(firstAttempt);
        throttler.resetForNewAttempt();

        ModelStreamProgressState secondAttempt = new ModelStreamProgressState(2);
        secondAttempt.recordNonEmptyDelta("fresh");
        throttler.onNonEmptyDelta(secondAttempt);

        assertThat(secondAttempt.snapshot().attempt()).isEqualTo(2);
        assertThat(secondAttempt.snapshot().receivedChars()).isEqualTo(5);
        assertThat(firstAttempt.snapshot().receivedChars()).isEqualTo(14);
    }

    @Test
    void providerStreamCloseDoesNotExposeRawIoMessageTest() {
        RuntimeException failure = new RuntimeException(
                "所有 AI 模型供应商调用均失败，最后错误: 流式调用 I/O 错误: closed");

        assertThat(GenerationFallbackPolicy.taskErrorMessage(failure))
                .isEqualTo(ProviderErrorSanitizer.PUBLIC_STREAM_INTERRUPTED);
        assertThat(GenerationFallbackPolicy.taskErrorMessage(failure)).doesNotContain("closed");
    }

    @Test
    void allProvidersFailedReturnsSafePublicMessageTest() {
        String summary = ProviderErrorSanitizer.buildProviderFailureSummary(
                3,
                "流式调用 I/O 错误: closed");

        assertThat(summary).contains(ProviderErrorSanitizer.PUBLIC_STREAM_INTERRUPTED);
        assertThat(summary).doesNotContain("closed");
        assertThat(summary).doesNotContain("I/O");
    }

    @Test
    void internalLogPreservesRootCauseTest() {
        String internal = ProviderErrorSanitizer.sanitize("流式调用 I/O 错误: closed");
        String publicMessage = ProviderErrorSanitizer.toPublicMessage("流式调用 I/O 错误: closed");

        assertThat(internal).contains("closed");
        assertThat(publicMessage).isEqualTo(ProviderErrorSanitizer.PUBLIC_STREAM_INTERRUPTED);
    }

    @Test
    void singleProviderFailureDoesNotClaimMultipleProvidersTest() {
        String summary = ProviderErrorSanitizer.buildProviderFailureSummary(
                1,
                "流式调用 I/O 错误: closed");

        assertThat(summary).startsWith("AI 模型调用失败：");
        assertThat(summary).doesNotContain("所有 AI 模型供应商");
    }

    @Test
    void providerFallbackStartsWithFreshAccumulatorTest() {
        ModelStreamProgressThrottler throttler = new ModelStreamProgressThrottler(progress -> { });
        ModelStreamProgressState providerA = new ModelStreamProgressState(1);
        providerA.recordNonEmptyDelta("aaaa");
        throttler.onNonEmptyDelta(providerA);
        throttler.resetForNewAttempt();

        ModelStreamProgressState providerB = new ModelStreamProgressState(2);
        providerB.recordNonEmptyDelta("bb");
        throttler.finalFlush(providerB);

        assertThat(providerB.snapshot().receivedChars()).isEqualTo(2);
        assertThat(providerA.snapshot().receivedChars()).isEqualTo(4);
    }

    @Test
    void streamCloseBeforeFirstDeltaClassificationTest() {
        assertThat(ProviderErrorSanitizer.isProviderTransportFailure(new IOException("closed"))).isTrue();
        assertThat(ProviderErrorSanitizer.toPublicMessage(new IOException("closed")))
                .isEqualTo(ProviderErrorSanitizer.PUBLIC_STREAM_INTERRUPTED);
    }

    @Test
    void streamCloseAfterDeltaClassificationTest() {
        RuntimeException failure = new RuntimeException(
                "流式调用 I/O 错误: closed",
                new IOException("closed"));

        assertThat(ProviderErrorSanitizer.isProviderTransportFailure(failure)).isTrue();
        assertThat(GenerationFallbackPolicy.taskErrorCode(failure)).isEqualTo("AI_STREAM_INTERRUPTED");
    }

    @Test
    void historicalTaskDetailErrorIsSanitizedForPublicReadTest() {
        ProviderErrorSanitizer.PublicTaskError publicError = ProviderErrorSanitizer.sanitizeStoredTaskError(
                "GENERATION_ERROR",
                "所有 AI 模型供应商调用均失败，最后错误: 流式调用 I/O 错误: closed");

        assertThat(publicError.errorCode()).isEqualTo("AI_STREAM_INTERRUPTED");
        assertThat(publicError.errorMessage()).isEqualTo(ProviderErrorSanitizer.PUBLIC_STREAM_INTERRUPTED);
        assertThat(publicError.errorMessage()).doesNotContain("closed");
    }
}
