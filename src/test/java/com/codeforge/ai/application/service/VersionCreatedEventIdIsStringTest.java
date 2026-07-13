package com.codeforge.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionCreatedEventIdIsStringTest {

    @Test
    void versionCreatedPayloadStoresIntegralVersionId() {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("versionId", 50L);
        node.put("versionNo", 1);

        assertThat(node.get("versionId").isIntegralNumber()).isTrue();
        assertThat(node.get("versionId").asText()).isEqualTo("50");
    }

    @Test
    void longVersionIdMustNotBecomeDoubleJsonNode() {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("versionId", Long.valueOf(2074335007354310656L));

        assertThat(node.get("versionId").isIntegralNumber()).isTrue();
        assertThat(node.get("versionId").isFloatingPointNumber()).isFalse();
    }
}
