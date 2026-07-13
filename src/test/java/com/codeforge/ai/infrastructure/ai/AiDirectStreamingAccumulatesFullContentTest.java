package com.codeforge.ai.infrastructure.ai;

import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests: streaming delta accumulation must match non-stream full content.
 */
class AiDirectStreamingAccumulatesFullContentTest {

    private OpenAiCompatibleModelGateway gateway;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        gateway = new OpenAiCompatibleModelGateway();
    }

    @Test
    void streamDeltasMustEqualNonStreamContent() throws Exception {
        String expected = buildRichExpectedContent();

        String streamContent = accumulateViaStreamChunks(buildStreamChunks(expected, "stop", 100, 200));

        assertThat(streamContent).isEqualTo(expected);
    }

    @Test
    void preservesUnicodeChineseAndEmoji() throws Exception {
        String expected = "你好，世界 🌍\n待办清单";

        String accumulated = accumulateViaStreamChunks(buildStreamChunks(expected, "stop", 10, 20));

        assertThat(accumulated).isEqualTo(expected);
        assertThat(accumulated.length()).isEqualTo(expected.length());
    }

    @Test
    void preservesEscapesBackslashNAndQuotes() throws Exception {
        String expected = "line1\\nline2\n\"hello\"\t\\\\";

        String accumulated = accumulateViaStreamChunks(buildStreamChunks(expected, "stop", 5, 15));

        assertThat(accumulated).isEqualTo(expected);
    }

    @Test
    void preservesJsonFragmentAndHtml() throws Exception {
        String expected = """
                {"files":[{"path":"index.html","content":"<!DOCTYPE html><html><body>中文</body></html>"}]}
                """;

        String accumulated = accumulateViaStreamChunks(buildStreamChunks(expected, "stop", 50, 120));

        assertThat(accumulated).isEqualTo(expected);
    }

    private String buildRichExpectedContent() {
        return """
                你好，世界
                line with literal \\n and real newline
                "hello" and backslash \\
                {"projectName":"待办","files":[{"path":"index.html","content":"<!DOCTYPE html><html lang=\\"zh-CN\\"><body>📝</body></html>"}]}
                """;
    }

    private String accumulateViaStreamChunks(List<String> sseJsonChunks) {
        StringBuilder fullContent = new StringBuilder();
        OpenAiCompatibleModelGateway.UsageHolder usageHolder = new OpenAiCompatibleModelGateway.UsageHolder();
        String finishReason = null;
        CollectingHandler handler = new CollectingHandler();

        handler.onStart();
        for (String chunk : sseJsonChunks) {
            finishReason = gateway.processSseData(chunk, handler, fullContent, usageHolder, finishReason);
        }
        handler.onComplete(ModelChatResult.success(
                fullContent.toString(),
                finishReason != null ? finishReason : "stop",
                usageHolder.promptTokens,
                usageHolder.completionTokens,
                usageHolder.totalTokens,
                10L,
                "test",
                "test-model"));

        assertThat(handler.deltas).isNotEmpty();
        assertThat(handler.result.content()).isEqualTo(fullContent.toString());
        return handler.result.content();
    }

    private List<String> buildStreamChunks(String content, String finishReason, long promptTokens, long completionTokens) {
        List<String> chunks = new ArrayList<>();
        int mid = content.length() / 3;
        int mid2 = (content.length() * 2) / 3;
        chunks.add(deltaChunk(content.substring(0, mid), null));
        chunks.add(deltaChunk(content.substring(mid, mid2), null));
        chunks.add(deltaChunk(content.substring(mid2), finishReason));
        chunks.add(usageChunk(promptTokens, completionTokens));
        return chunks;
    }

    private String buildNonStreamResponse(String content, String finishReason, long promptTokens, long completionTokens)
            throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode choices = root.putArray("choices");
        ObjectNode choice = choices.addObject();
        ObjectNode message = choice.putObject("message");
        message.put("content", content);
        choice.put("finish_reason", finishReason);
        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);
        return mapper.writeValueAsString(root);
    }

    private String deltaChunk(String delta, String finishReason) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode choices = root.putArray("choices");
            ObjectNode choice = choices.addObject();
            ObjectNode deltaNode = choice.putObject("delta");
            deltaNode.put("content", delta);
            if (finishReason != null) {
                choice.put("finish_reason", finishReason);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private String usageChunk(long promptTokens, long completionTokens) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ObjectNode usage = root.putObject("usage");
            usage.put("prompt_tokens", promptTokens);
            usage.put("completion_tokens", completionTokens);
            usage.put("total_tokens", promptTokens + completionTokens);
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private static final class CollectingHandler implements ModelStreamHandler {
        private final List<String> deltas = new ArrayList<>();
        private ModelChatResult result;

        @Override
        public void onStart() {
        }

        @Override
        public void onDelta(String delta) {
            deltas.add(delta);
        }

        @Override
        public void onError(Throwable error) {
            throw new AssertionError(error);
        }

        @Override
        public void onComplete(ModelChatResult modelChatResult) {
            this.result = modelChatResult;
        }
    }
}
