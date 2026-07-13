package com.codeforge.ai.infrastructure.bootstrap;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiDirectDeepSeekCompatibilityTest {

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
    void shouldInsertDeepseekProviderWhenMissing() throws Exception {
        given(modelProviderEntityMapper.findByProviderCode("auto")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("rule")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("openai")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("qwen")).willReturn(null);
        given(modelProviderEntityMapper.insertProvider(any())).willReturn(1);

        initializer.run(null);

        ArgumentCaptor<ModelProviderEntity> captor = ArgumentCaptor.forClass(ModelProviderEntity.class);
        verify(modelProviderEntityMapper, org.mockito.Mockito.times(4)).insertProvider(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ModelProviderEntity::getProviderCode)
                .contains("deepseek");
        ModelProviderEntity deepseek = captor.getAllValues().stream()
                .filter(entity -> "deepseek".equals(entity.getProviderCode()))
                .findFirst()
                .orElseThrow();
        assertThat(deepseek.getDefaultModel()).isEqualTo("deepseek-chat");
        assertThat(deepseek.getBaseUrl()).isEqualTo("https://api.deepseek.com");
    }

    @Test
    void shouldNotRewriteProviderCodeBackToOpenAi() throws Exception {
        given(modelProviderEntityMapper.findByProviderCode("auto")).willReturn(null);
        given(modelProviderEntityMapper.findByProviderCode("rule")).willReturn(existing("rule"));
        given(modelProviderEntityMapper.findByProviderCode("deepseek")).willReturn(existing("deepseek"));
        given(modelProviderEntityMapper.findByProviderCode("openai")).willReturn(existing("openai"));
        given(modelProviderEntityMapper.findByProviderCode("qwen")).willReturn(existing("qwen"));

        initializer.run(null);

        verify(modelProviderEntityMapper, never()).insertProvider(any());
    }

    private ModelProviderEntity existing(String code) {
        return ModelProviderEntity.builder()
                .id(1L)
                .providerCode(code)
                .providerName(code)
                .status("ACTIVE")
                .build();
    }
}
