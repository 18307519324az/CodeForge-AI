package com.codeforge.ai.infrastructure.bootstrap;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProviderCatalogInitializerTest {

    @Mock
    private ModelProviderEntityMapper modelProviderEntityMapper;

    @InjectMocks
    private ProviderCatalogInitializer initializer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(initializer, "openAiBaseUrl", "https://api.openai.com/v1");
        ReflectionTestUtils.setField(initializer, "openAiModelName", "gpt-4.1-mini");
    }

    @Test
    void shouldInsertRuleAndStandardAiProvidersWhenMissing() throws Exception {
        given(modelProviderEntityMapper.findByProviderCode("auto")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("rule")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("openai")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("qwen")).willReturn(null);
        given(modelProviderEntityMapper.insertProvider(any())).willReturn(1);

        initializer.run(null);

        ArgumentCaptor<ModelProviderEntity> captor = ArgumentCaptor.forClass(ModelProviderEntity.class);
        verify(modelProviderEntityMapper, times(4)).insertProvider(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ModelProviderEntity::getProviderCode)
                .containsExactly("rule", "deepseek", "openai", "qwen");
        assertThat(captor.getAllValues().getFirst().getApiProtocol()).isEqualTo("RULE_BASED");
        assertThat(captor.getAllValues().get(1).getApiKeyEnv()).isEqualTo("DEEPSEEK_API_KEY");
    }

    @Test
    void autoPseudoProviderIsNeverCataloguedTest() throws Exception {
        given(modelProviderEntityMapper.findByProviderCode("auto")).willReturn(existing("auto"));
        given(modelProviderEntityMapper.findByProviderCode("rule")).willReturn(existing("rule"));
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(existing("deepseek"));
        given(modelProviderEntityMapper.findByProviderCode("openai")).willReturn(existing("openai"));
        given(modelProviderEntityMapper.findByProviderCode("qwen")).willReturn(existing("qwen"));
        given(modelProviderEntityMapper.softDeleteByProviderCode(eq("auto"), any(), any())).willReturn(1);

        initializer.run(null);

        verify(modelProviderEntityMapper).softDeleteByProviderCode(eq("auto"), any(), any());
        verify(modelProviderEntityMapper, never()).insertProvider(any());
    }

    @Test
    void shouldSkipExistingProviders() throws Exception {
        given(modelProviderEntityMapper.findByProviderCode("auto")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("rule")).willReturn(existing("rule"));
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(existing("deepseek"));
        given(modelProviderEntityMapper.findByProviderCode("openai")).willReturn(existing("openai"));
        given(modelProviderEntityMapper.findByProviderCode("qwen")).willReturn(existing("qwen"));

        initializer.run(null);

        verify(modelProviderEntityMapper, never()).insertProvider(any());
    }

    @Test
    void shouldSyncExistingAiProviderFromEnv() throws Exception {
        ModelProviderEntity existing = ModelProviderEntity.builder()
                .id(1L)
                .providerCode("openai")
                .providerName("OpenAI Compatible")
                .baseUrl("https://api.openai.com/v1")
                .authMode("API_KEY")
                .apiProtocol("OPENAI_COMPATIBLE")
                .apiKeyEnv("OPENAI_API_KEY")
                .defaultModel("gpt-4.1-mini")
                .priority(10)
                .status("ACTIVE")
                .build();
        given(modelProviderEntityMapper.findByProviderCode("auto")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("rule")).willReturn(existing("rule"));
        given(modelProviderEntityMapper.findByProviderCode("openai")).willReturn(existing);
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(fullExisting(
                "deepseek", "https://api.deepseek.com", "deepseek-chat", "DEEPSEEK_API_KEY", 10));
        given(modelProviderEntityMapper.findByProviderCode("qwen")).willReturn(fullExisting(
                "qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "DASHSCOPE_API_KEY", 30));
        given(modelProviderEntityMapper.updateAiCatalog(any())).willReturn(1);

        ReflectionTestUtils.setField(initializer, "openAiBaseUrl", "https://api.deepseek.com");
        ReflectionTestUtils.setField(initializer, "openAiModelName", "deepseek-chat");

        initializer.run(null);

        ArgumentCaptor<ModelProviderEntity> captor = ArgumentCaptor.forClass(ModelProviderEntity.class);
        verify(modelProviderEntityMapper).updateAiCatalog(captor.capture());
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(captor.getValue().getDefaultModel()).isEqualTo("deepseek-chat");
        verify(modelProviderEntityMapper, never()).insertProvider(any());
    }

    private ModelProviderEntity existing(String code) {
        return ModelProviderEntity.builder()
                .id(1L)
                .providerCode(code)
                .providerName(code)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ModelProviderEntity fullExisting(String code, String baseUrl, String defaultModel, String apiKeyEnv, int priority) {
        return ModelProviderEntity.builder()
                .id(1L)
                .providerCode(code)
                .providerName(code)
                .baseUrl(baseUrl)
                .authMode("API_KEY")
                .apiProtocol("OPENAI_COMPATIBLE")
                .apiKeyEnv(apiKeyEnv)
                .defaultModel(defaultModel)
                .priority(priority)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
