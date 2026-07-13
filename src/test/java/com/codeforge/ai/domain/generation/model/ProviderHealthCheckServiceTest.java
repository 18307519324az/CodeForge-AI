package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProviderHealthCheckServiceTest {

    @Mock
    private ModelProviderRoutingConfigService routingConfigService;

    private ProviderHealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        healthCheckService = new ProviderHealthCheckService(routingConfigService);
    }

    @Test
    void ruleProviderUsesLocalHealthCheckTest() {
        ModelProviderEntity rule = ruleProvider();
        given(routingConfigService.isActiveStatus("ACTIVE")).willReturn(true);
        given(routingConfigService.isRuleProvider(rule)).willReturn(true);

        ProviderHealthCheckService.ProviderHealthCheckResult result = healthCheckService.check(rule);

        assertThat(result.healthy()).isTrue();
        assertThat(result.message()).contains("规则引擎");
    }

    @Test
    void missingKeyProviderIsNotEligibleTest() {
        ModelProviderEntity openai = aiProvider("openai", null);
        given(routingConfigService.isActiveStatus("ACTIVE")).willReturn(true);
        given(routingConfigService.isRuleProvider(openai)).willReturn(false);
        given(routingConfigService.isRuntimeConfigured(openai)).willReturn(false);

        ProviderHealthCheckService.ProviderHealthCheckResult result = healthCheckService.check(openai);

        assertThat(result.healthy()).isFalse();
        assertThat(result.message()).startsWith("UNCONFIGURED:");
    }

    @Test
    void oneProviderHealthFailureDoesNotFailWholeBatchTest() {
        ModelProviderEntity deepseek = aiProvider("deepseek", "DEEPSEEK_API_KEY");
        given(routingConfigService.isActiveStatus("ACTIVE")).willReturn(true);
        given(routingConfigService.isRuleProvider(deepseek)).willReturn(false);
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);

        ProviderHealthCheckService.ProviderHealthCheckResult healthy = healthCheckService.check(deepseek);
        ProviderHealthCheckService.ProviderHealthCheckResult missingKey =
                healthCheckService.check(aiProvider("openai", "OPENAI_API_KEY"));

        assertThat(healthy.healthy()).isTrue();
        assertThat(missingKey.healthy()).isFalse();
    }

    @Test
    void configuredAiProviderPassesConfigHealthCheckTest() {
        ModelProviderEntity deepseek = aiProvider("deepseek", "DEEPSEEK_API_KEY");
        given(routingConfigService.isActiveStatus("ACTIVE")).willReturn(true);
        given(routingConfigService.isRuleProvider(deepseek)).willReturn(false);
        given(routingConfigService.isRuntimeConfigured(deepseek)).willReturn(true);

        ProviderHealthCheckService.ProviderHealthCheckResult result = healthCheckService.check(deepseek);

        assertThat(result.healthy()).isTrue();
        assertThat(result.message()).isEqualTo("配置检查通过");
    }

    private ModelProviderEntity aiProvider(String code, String apiKeyEnv) {
        return ModelProviderEntity.builder()
                .id(1L)
                .providerCode(code)
                .providerName(code)
                .baseUrl("https://api.example.com")
                .authMode("API_KEY")
                .apiKeyEnv(apiKeyEnv)
                .apiProtocol("OPENAI_COMPATIBLE")
                .status("ACTIVE")
                .build();
    }

    private ModelProviderEntity ruleProvider() {
        return ModelProviderEntity.builder()
                .id(99L)
                .providerCode("rule")
                .providerName("Rule")
                .authMode("NONE")
                .apiProtocol("RULE_BASED")
                .status("ACTIVE")
                .build();
    }
}
