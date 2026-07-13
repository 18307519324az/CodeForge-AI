package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModelProviderSelector {

    private final ProviderCandidateResolver candidateResolver;

    public List<ModelProviderEntity> selectAvailable() {
        return candidateResolver.resolveAvailableProviders();
    }

    public List<ModelProviderEntity> selectAiProviders() {
        return candidateResolver.resolveAiProviders();
    }

    public ModelProviderEntity selectRuleProvider() {
        return candidateResolver.resolveRuleProvider();
    }

    public boolean hasConfiguredAiProvider() {
        return candidateResolver.hasConfiguredAiProvider();
    }
}
