package com.codeforge.ai.application.dto.prompt;

import com.codeforge.ai.shared.request.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PublishedPromptTemplateQueryRequest extends PageRequest {

    private String keyword;

    /**
     * Maps to prompt_template.template_scene in runtime schema.
     */
    private String templateScene;
}
