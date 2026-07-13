package com.codeforge.ai.infrastructure.bootstrap;

import com.codeforge.ai.domain.generation.model.CredentialSource;
import com.codeforge.ai.domain.generation.model.ProviderCatalogSupport;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.persistence.mapper.ModelProviderEntityMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 幂等初始化 model_provider 目录（rule + 标准 AI provider），不写入任何 API Key。
 */
@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
public class ProviderCatalogInitializer implements ApplicationRunner {

    private static final long SYSTEM_USER_ID = 1L;

    private final ModelProviderEntityMapper modelProviderEntityMapper;

    @Value("${langchain4j.open-ai.chat-model.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name:gpt-4.1-mini}")
    private String openAiModelName;

    @Override
    public void run(ApplicationArguments args) {
        reconcilePseudoProviders();
        ensureRuleProvider();
        ensureStandardAiProviders();
    }

    private void reconcilePseudoProviders() {
        for (String pseudoCode : ProviderCatalogSupport.PSEUDO_PROVIDER_CODES) {
            ModelProviderEntity existing = modelProviderEntityMapper.findByProviderCode(pseudoCode);
            if (existing != null) {
                modelProviderEntityMapper.softDeleteByProviderCode(
                        pseudoCode,
                        SYSTEM_USER_ID,
                        LocalDateTime.now());
                log.info("Reconciled pseudo provider catalog entry: providerCode={}", pseudoCode);
            }
        }
    }

    private void ensureRuleProvider() {
        upsertProvider(
                "rule",
                "Rule Generator",
                null,
                "NONE",
                "RULE_BASED",
                null,
                CredentialSource.NONE.code(),
                "rule-based",
                999
        );
    }

    private void ensureStandardAiProviders() {
        upsertProvider(
                "deepseek",
                "DeepSeek",
                "https://api.deepseek.com",
                "API_KEY",
                "OPENAI_COMPATIBLE",
                "DEEPSEEK_API_KEY",
                CredentialSource.ENV.code(),
                "deepseek-chat",
                10
        );
        upsertProvider(
                "openai",
                "OpenAI Compatible",
                openAiBaseUrl,
                "API_KEY",
                "OPENAI_COMPATIBLE",
                "OPENAI_API_KEY",
                CredentialSource.ENV.code(),
                openAiModelName,
                20
        );
        upsertProvider(
                "qwen",
                "Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "API_KEY",
                "OPENAI_COMPATIBLE",
                "DASHSCOPE_API_KEY",
                CredentialSource.ENV.code(),
                "qwen-plus",
                30
        );
    }

    private void upsertProvider(String providerCode,
                                String providerName,
                                String baseUrl,
                                String authMode,
                                String apiProtocol,
                                String apiKeyEnv,
                                String credentialSource,
                                String defaultModel,
                                int priority) {
        ModelProviderEntity existing = modelProviderEntityMapper.findByProviderCode(providerCode);
        if (existing != null) {
            if ("OPENAI_COMPATIBLE".equalsIgnoreCase(apiProtocol)) {
                syncAiProviderConfig(existing, baseUrl, defaultModel, apiKeyEnv);
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ModelProviderEntity entity = ModelProviderEntity.builder()
                .providerCode(providerCode)
                .providerName(providerName)
                .baseUrl(baseUrl)
                .authMode(authMode)
                .apiProtocol(apiProtocol)
                .apiKeyEnv(apiKeyEnv)
                .credentialSource(credentialSource)
                .defaultModel(defaultModel)
                .priority(priority)
                .status("ACTIVE")
                .createdBy(SYSTEM_USER_ID)
                .updatedBy(SYSTEM_USER_ID)
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(0)
                .build();
        modelProviderEntityMapper.insertProvider(entity);
        log.info("Initialized model provider catalog entry: providerCode={}", providerCode);
    }

    private void syncAiProviderConfig(ModelProviderEntity existing,
                                      String baseUrl,
                                      String defaultModel,
                                      String apiKeyEnv) {
        boolean changed = false;
        if (baseUrl != null && !baseUrl.equals(existing.getBaseUrl())) {
            existing.setBaseUrl(baseUrl);
            changed = true;
        }
        if (defaultModel != null && !defaultModel.equals(existing.getDefaultModel())) {
            existing.setDefaultModel(defaultModel);
            changed = true;
        }
        if (apiKeyEnv != null && !apiKeyEnv.equals(existing.getApiKeyEnv())) {
            existing.setApiKeyEnv(apiKeyEnv);
            changed = true;
        }
        if (!changed) {
            return;
        }
        existing.setUpdatedBy(SYSTEM_USER_ID);
        existing.setUpdatedAt(LocalDateTime.now());
        modelProviderEntityMapper.updateAiCatalog(existing);
        log.info("Synced model provider catalog from env: providerCode={}, baseUrl={}, model={}",
                existing.getProviderCode(), existing.getBaseUrl(), existing.getDefaultModel());
    }
}
