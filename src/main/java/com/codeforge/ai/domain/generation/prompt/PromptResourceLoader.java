package com.codeforge.ai.domain.generation.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptResourceLoader {
    public String load(String resourceName) {
        try {
            ClassPathResource resource = new ClassPathResource("prompt/" + resourceName);
            if (!resource.exists()) throw new RuntimeException("Prompt 文件不存在: " + resourceName);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Prompt 文件加载失败: " + resourceName, e);
        }
    }
}
