package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderHealthCheckService {

    private final ModelProviderRoutingConfigService routingConfigService;

    public ProviderHealthCheckResult check(ModelProviderEntity provider) {
        if (provider == null) {
            return ProviderHealthCheckResult.unhealthy("模型提供方不存在");
        }
        if (!routingConfigService.isActiveStatus(provider.getStatus())) {
            return ProviderHealthCheckResult.unhealthy("提供方未启用");
        }
        if (routingConfigService.isRuleProvider(provider)) {
            return ProviderHealthCheckResult.healthy("规则引擎已就绪");
        }
        if (!routingConfigService.isRuntimeConfigured(provider)) {
            String message = "API Key 未配置或环境变量不可用";
            if (provider.getApiKeyEnv() != null && !provider.getApiKeyEnv().isBlank()) {
                message = "环境变量 " + provider.getApiKeyEnv() + " 未配置";
            }
            return ProviderHealthCheckResult.unhealthy("UNCONFIGURED: " + message);
        }
        if (provider.getBaseUrl() == null || provider.getBaseUrl().isBlank()) {
            return ProviderHealthCheckResult.unhealthy("Base URL 未配置");
        }
        return ProviderHealthCheckResult.healthy("配置检查通过");
    }

    public record ProviderHealthCheckResult(boolean healthy, String message) {
        public static ProviderHealthCheckResult healthy(String message) {
            return new ProviderHealthCheckResult(true, ProviderErrorSanitizer.sanitize(message));
        }

        public static ProviderHealthCheckResult unhealthy(String message) {
            return new ProviderHealthCheckResult(false, ProviderErrorSanitizer.sanitize(message));
        }
    }
}
