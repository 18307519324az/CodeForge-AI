package com.codeforge.ai.infrastructure.ai;

import com.codeforge.ai.domain.generation.model.ModelMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AiDirectDeepSeekCompatibilityTest {

    @Test
    void shouldUseConfiguredMaxTokensAndTemperatureInRequestBody() {
        OpenAiCompatibleModelGateway gateway = new OpenAiCompatibleModelGateway();
        ReflectionTestUtils.setField(gateway, "configuredMaxTokens", 8192);
        ReflectionTestUtils.setField(gateway, "configuredTemperature", 0.2d);

        String body = gateway.buildBody("deepseek-chat",
                List.of(ModelMessage.system("只返回 JSON"), ModelMessage.user("生成待办清单")),
                null, null, false);

        assertThat(body).contains("\"model\":\"deepseek-chat\"");
        assertThat(body).contains("\"max_tokens\":8192");
        assertThat(body).contains("\"temperature\":0.2");
        assertThat(gateway.supports("deepseek")).isTrue();
    }
}
