package com.codeforge.ai.application.generation;

import com.codeforge.ai.domain.generation.prompt.PromptResourceLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCodegenPromptSnapshotTest {

    private final PromptResourceLoader loader = new PromptResourceLoader();

    @Test
    void htmlPromptShouldRequireJsonOnlyOutput() {
        String prompt = loader.load("codegen-html-system-prompt.txt");

        assertThat(prompt).startsWith("Only output valid JSON.");
        assertThat(prompt).contains("Never output markdown.");
        assertThat(prompt).contains("Never use ```.");
        assertThat(prompt).doesNotContain("\"projectName\"");
        assertThat(prompt).doesNotContain("\"description\"");
        assertThat(prompt).contains("\"path\": \"index.html\"");
        assertThat(prompt).contains("{\"files\":[]}");
    }

    @Test
    void retryInstructionShouldDemandStrictJson() {
        assertThat(AiCodegenPromptBuilder.RETRY_USER_INSTRUCTION)
                .contains("Return valid JSON only")
                .contains("Never output markdown");
    }
}
