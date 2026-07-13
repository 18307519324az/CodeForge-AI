package com.codeforge.ai.domain.generation.model;

import com.codeforge.ai.domain.generation.ModelGateway;
import com.codeforge.ai.domain.generation.RuleBasedModelGateway;
import com.codeforge.ai.domain.generation.StreamingModelGateway;
import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.codeforge.ai.infrastructure.ai.OpenAiCompatibleModelGateway;
import com.codeforge.ai.shared.exception.BusinessException;
import com.codeforge.ai.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ModelGatewayFactory {
    private final RuleBasedModelGateway ruleGateway;
    private final OpenAiCompatibleModelGateway openAiGateway;

    public ModelGateway getGateway(ModelProviderEntity provider) {
        if (provider == null || provider.getApiProtocol() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "模型供应商协议不能为空");
        }
        return switch (provider.getApiProtocol()) {
            case "RULE_BASED" -> ruleGateway;
            case "OPENAI_COMPATIBLE" -> openAiGateway;
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "暂不支持的模型协议：" + provider.getApiProtocol());
        };
    }

    /**
     * Return a {@link StreamingModelGateway} for the given provider.
     * @throws BusinessException if the gateway does not support streaming
     */
    public StreamingModelGateway getStreamingGateway(ModelProviderEntity provider) {
        ModelGateway gateway = getGateway(provider);
        if (gateway instanceof StreamingModelGateway sg) {
            return sg;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR,
                "Provider " + provider.getProviderCode() + " 不支持流式调用");
    }
}
