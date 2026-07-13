package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.model.ProviderErrorSanitizer;
import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationFallbackPolicyTransportTest {

    @Test
    void detectsAsyncContextTransportFailure() {
        RuntimeException failure = new RuntimeException(
                "A non-container (application) thread attempted to use the AsyncContext after an error had occurred");
        assertThat(GenerationFallbackPolicy.isSseTransportFailure(failure)).isTrue();
    }

    @Test
    void taskErrorMessageSanitizesTransportFailure() {
        IOException failure = new IOException("Broken pipe");
        assertThat(GenerationFallbackPolicy.taskErrorMessage(failure))
                .isEqualTo("生成过程中发生内部通信异常，请稍后重试");
    }

    @Test
    void taskErrorMessageSanitizesServletDetails() {
        RuntimeException failure = new RuntimeException("org.apache.catalina.connector.ClientAbortException");
        assertThat(GenerationFallbackPolicy.taskErrorMessage(failure))
                .isEqualTo(ProviderErrorSanitizer.PUBLIC_INTERNAL_ERROR);
    }
}
