package com.codeforge.ai.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicationSlugGeneratorTest {

    @Test
    void shouldGenerateAsciiSlugWithSuffix() {
        String slug = PublicationSlugGenerator.generateSlug("Customer Management");
        assertThat(slug).matches("customer-management-[a-z0-9]{6}");
    }

    @Test
    void shouldFallbackToAppPrefixForChineseOnlyTitle() {
        String slug = PublicationSlugGenerator.generateSlug("客户管理后台");
        assertThat(slug).matches("app-[a-z0-9]{6}");
    }
}
