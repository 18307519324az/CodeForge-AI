package com.codeforge.ai.infrastructure.ai;

import com.codeforge.ai.domain.generation.GenerationContext;
import com.codeforge.ai.domain.generation.ModelGateway;
import com.codeforge.ai.domain.generation.ModelStreamHandler;
import com.codeforge.ai.domain.generation.StreamingModelGateway;
import com.codeforge.ai.domain.generation.model.ModelChatRequest;
import com.codeforge.ai.domain.generation.model.ModelChatResult;
import com.codeforge.ai.domain.generation.model.ModelMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleModelGateway implements ModelGateway, StreamingModelGateway {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleModelGateway.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${codeforge.ai.max-tokens:8192}")
    private int configuredMaxTokens;

    @Value("${codeforge.ai.temperature:0.2}")
    private double configuredTemperature;

    @Override
    public boolean supports(String providerCode) {
        return !"rule".equalsIgnoreCase(providerCode);
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public String generate(GenerationContext context) {
        if (context.apiKey() == null || context.apiKey().isBlank()) {
            throw new RuntimeException("API Key 未配置");
        }
        String systemPrompt = context.systemPrompt() != null
                ? context.systemPrompt()
                : "You are a code generator. Generate project files based on the user requirement.";
        ModelChatRequest request = ModelChatRequest.of(
                context.providerCode(),
                context.baseUrl(),
                context.apiKey(),
                context.modelName() != null ? context.modelName() : "gpt-4.1-mini",
                List.of(ModelMessage.system(systemPrompt), ModelMessage.user(context.requirement())),
                context.appId(),
                context.taskId(),
                context.userId(),
                configuredMaxTokens,
                configuredTemperature
        );
        return chat(request).content();
    }

    public ModelChatResult chat(ModelChatRequest request) {
        long start = System.currentTimeMillis();
        try {
            String url = buildUrl(request.baseUrl());
            String body = buildBody(request.modelName(), request.messages(),
                    request.maxTokens(), request.temperature(), false);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + request.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(120))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() >= 400) {
                log.warn("Model call failed: provider={}, status={}, body={}",
                        request.providerCode(), response.statusCode(), abbreviate(response.body()));
                throw new RuntimeException("模型调用失败: HTTP " + response.statusCode());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("模型响应格式异常: 无 choices");
            }
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            String content = message != null && message.has("content") ? message.get("content").asText() : "";
            String finishReason = firstChoice.has("finish_reason")
                    ? firstChoice.get("finish_reason").asText("stop")
                    : "stop";

            JsonNode usage = root.get("usage");
            long promptTokens = usage != null && usage.has("prompt_tokens") ? usage.get("prompt_tokens").asLong() : 0L;
            long completionTokens = usage != null && usage.has("completion_tokens")
                    ? usage.get("completion_tokens").asLong() : 0L;
            long totalTokens = usage != null && usage.has("total_tokens") ? usage.get("total_tokens").asLong() : 0L;

            log.info("Model call success: provider={}, model={}, tokens={}/{}, latency={}ms",
                    request.providerCode(), request.modelName(), promptTokens, completionTokens, latency);
            return ModelChatResult.success(content, finishReason, promptTokens, completionTokens, totalTokens,
                    latency, request.providerCode(), request.modelName());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Model call error: provider={}, error={}", request.providerCode(), exception.getMessage());
            throw new RuntimeException("模型调用失败: " + exception.getMessage());
        }
    }

    @Override
    public void streamChat(GenerationContext context, ModelStreamHandler handler) {
        if (context.apiKey() == null || context.apiKey().isBlank()) {
            handler.onError(new RuntimeException("API Key 未配置"));
            return;
        }
        String systemPrompt = context.systemPrompt() != null
                ? context.systemPrompt()
                : "You are a code generator. Generate project files based on the user requirement.";
        ModelChatRequest request = ModelChatRequest.of(
                context.providerCode(),
                context.baseUrl(),
                context.apiKey(),
                context.modelName() != null ? context.modelName() : "gpt-4.1-mini",
                List.of(ModelMessage.system(systemPrompt), ModelMessage.user(context.requirement())),
                context.appId(),
                context.taskId(),
                context.userId(),
                configuredMaxTokens,
                configuredTemperature
        );
        streamChatRequest(request, handler);
    }

    public void streamChatRequest(ModelChatRequest request, ModelStreamHandler handler) {
        long start = System.currentTimeMillis();
        try {
            String url = buildUrl(request.baseUrl());
            String body = buildBody(request.modelName(), request.messages(),
                    request.maxTokens(), request.temperature(), true);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + request.apiKey())
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofMinutes(5))
                    .build();

            handler.onStart();
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                handler.onError(new RuntimeException(
                        "模型流式调用失败: HTTP " + response.statusCode() + " " + readErrorBody(response)));
                return;
            }

            StringBuilder fullContent = new StringBuilder();
            String pendingData = null;
            String finishReason = null;
            UsageHolder usageHolder = new UsageHolder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        if (pendingData != null) {
                            finishReason = processSseData(pendingData, handler, fullContent, usageHolder, finishReason);
                            pendingData = null;
                        }
                        continue;
                    }
                    if (line.startsWith("data: [DONE]")) {
                        break;
                    }
                    if (line.startsWith("data: ")) {
                        String json = line.substring(6).trim();
                        if (json.endsWith("}")) {
                            if (pendingData != null) {
                                finishReason = processSseData(pendingData, handler, fullContent, usageHolder, finishReason);
                            }
                            finishReason = processSseData(json, handler, fullContent, usageHolder, finishReason);
                            pendingData = null;
                        } else {
                            pendingData = pendingData == null ? json : pendingData + "\n" + json;
                        }
                        continue;
                    }
                    if (pendingData != null) {
                        finishReason = processSseData(pendingData, handler, fullContent, usageHolder, finishReason);
                        pendingData = null;
                    }
                }
            }

            if (pendingData != null) {
                finishReason = processSseData(pendingData, handler, fullContent, usageHolder, finishReason);
            }

            long latency = System.currentTimeMillis() - start;
            log.info("Stream complete: provider={}, model={}, chars={}, latency={}ms",
                    request.providerCode(), request.modelName(), fullContent.length(), latency);
            handler.onComplete(ModelChatResult.success(fullContent.toString(),
                    finishReason != null ? finishReason : "stop",
                    usageHolder.promptTokens,
                    usageHolder.completionTokens,
                    usageHolder.totalTokens,
                    latency,
                    request.providerCode(),
                    request.modelName()));
        } catch (IOException exception) {
            log.warn("Stream I/O error: provider={}, error={}", request.providerCode(), exception.getMessage(), exception);
            handler.onError(new RuntimeException("流式调用 I/O 错误: " + exception.getMessage(), exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Stream interrupted: provider={}", request.providerCode());
            handler.onError(new RuntimeException("流式调用被中断"));
        } catch (Exception exception) {
            log.warn("Stream unexpected error: provider={}, error={}", request.providerCode(), exception.getMessage());
            handler.onError(new RuntimeException("流式调用异常: " + exception.getMessage()));
        }
    }

    static class UsageHolder {
        long promptTokens;
        long completionTokens;
        long totalTokens;
    }

    String processSseData(String json,
                          ModelStreamHandler handler,
                          StringBuilder fullContent,
                          UsageHolder usageHolder,
                          String currentFinishReason) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode errorNode = root.get("error");
            if (errorNode != null) {
                String message = errorNode.has("message") ? errorNode.get("message").asText() : "unknown error";
                handler.onError(new RuntimeException("模型返回错误: " + message));
                return currentFinishReason;
            }

            JsonNode usageNode = root.get("usage");
            if (usageNode != null) {
                if (usageNode.has("prompt_tokens")) {
                    usageHolder.promptTokens = usageNode.get("prompt_tokens").asLong();
                }
                if (usageNode.has("completion_tokens")) {
                    usageHolder.completionTokens = usageNode.get("completion_tokens").asLong();
                }
                if (usageNode.has("total_tokens")) {
                    usageHolder.totalTokens = usageNode.get("total_tokens").asLong();
                }
            }

            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return currentFinishReason;
            }
            JsonNode firstChoice = choices.get(0);
            if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isNull()) {
                currentFinishReason = firstChoice.get("finish_reason").asText();
            }

            JsonNode delta = firstChoice.get("delta");
            if (delta != null && delta.has("content")) {
                String content = delta.get("content").asText();
                if (content != null && !content.isEmpty()) {
                    fullContent.append(content);
                    handler.onDelta(content);
                }
            }
            return currentFinishReason;
        } catch (Exception exception) {
            log.debug("SSE data parse warning: {}", exception.getMessage());
            return currentFinishReason;
        }
    }

    private String readErrorBody(HttpResponse<InputStream> response) {
        try (InputStream inputStream = response.body()) {
            return new String(inputStream.readNBytes(500), StandardCharsets.UTF_8).trim();
        } catch (Exception exception) {
            return "";
        }
    }

    private String buildUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed.endsWith("/v1") ? trimmed + "/chat/completions" : trimmed + "/v1/chat/completions";
    }

    String buildBody(String model, List<ModelMessage> messages, Integer maxTokens, Double temperature, boolean stream) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("model", model);
            ArrayNode messageArray = root.putArray("messages");
            for (ModelMessage message : messages) {
                ObjectNode messageNode = messageArray.addObject();
                messageNode.put("role", message.role());
                messageNode.put("content", message.content());
            }
            root.put("max_tokens", maxTokens != null ? maxTokens : configuredMaxTokens);
            root.put("temperature", temperature != null ? temperature : configuredTemperature);
            root.put("stream", stream);
            return mapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to build request body", exception);
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 200 ? value.substring(0, 200) : value;
    }
}
