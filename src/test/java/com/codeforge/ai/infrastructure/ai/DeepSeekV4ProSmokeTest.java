package com.codeforge.ai.infrastructure.ai;

import com.codeforge.ai.domain.generation.model.ModelChatRequest;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeepSeekV4ProSmokeTest {

    private static final String MODEL = "deepseek-v4-pro";
    private static final String BASE_URL = "https://api.deepseek.com";

    @Autowired
    private OpenAiCompatibleModelGateway modelGateway;

    @Test
    void shouldCallDeepSeekV4ProWithMinimalPrompt() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "DEEPSEEK_API_KEY is NOT_SET");

        ModelChatRequest request = ModelChatRequest.of(
                "deepseek",
                BASE_URL,
                apiKey,
                MODEL,
                List.of(ModelMessage.user("Reply with exactly: pong")),
                1L,
                1L,
                1L,
                32,
                0.0
        );

        long startedAt = System.currentTimeMillis();
        ModelChatResult result = modelGateway.chat(request);
        long latencyMs = System.currentTimeMillis() - startedAt;

        assertThat(result.content()).isNotBlank();
        assertThat(result.promptTokens()).isNotNull().isGreaterThan(0L);
        assertThat(result.completionTokens()).isNotNull().isGreaterThan(0L);
        assertThat(result.latencyMs()).isNotNull().isGreaterThan(0L);
    }
}
