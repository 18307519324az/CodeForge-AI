package com.codeforge.ai.domain.generation.business;

import java.util.List;

public record BusinessPreset(
        String businessType,
        List<String> appTypes,
        List<String> modules,
        List<String> entities,
        List<String> workflow,
        List<String> fields,
        List<String> buttons,
        List<String> charts,
        List<String> interactions,
        List<String> mustAvoid
) {
}
