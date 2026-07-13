package com.codeforge.ai.domain.prompt.model;

import com.codeforge.ai.domain.generation.model.ModelMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public final class PromptFingerprintHasher {

    private PromptFingerprintHasher() {
    }

    public static ResolvedGenerationPrompt withFingerprints(
            Long templateId,
            Long templateVersionId,
            String templateCode,
            Integer versionNo,
            String renderedSystemPrompt,
            String renderedUserPrompt) {
        PromptFingerprint fingerprint = hash(renderedSystemPrompt, renderedUserPrompt);
        return new ResolvedGenerationPrompt(
                templateId,
                templateVersionId,
                templateCode,
                versionNo,
                renderedSystemPrompt,
                renderedUserPrompt,
                fingerprint.systemSha256(),
                fingerprint.userSha256(),
                fingerprint.combined());
    }

    public static PromptFingerprint fromMessages(List<ModelMessage> messages) {
        String system = extractRoleContent(messages, "system");
        String user = combineUserMessages(messages);
        return hash(system, user);
    }

    public static PromptFingerprint hash(String systemPrompt, String userPrompt) {
        String normalizedSystem = systemPrompt == null ? "" : systemPrompt;
        String normalizedUser = userPrompt == null ? "" : userPrompt;
        String systemSha256 = sha256(normalizedSystem);
        String userSha256 = sha256(normalizedUser);
        String combined = sha256(normalizedSystem + "\n---\n" + normalizedUser);
        return new PromptFingerprint(systemSha256, userSha256, combined);
    }

    private static String extractRoleContent(List<ModelMessage> messages, String role) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .filter(message -> role.equalsIgnoreCase(message.role()))
                .map(ModelMessage::content)
                .findFirst()
                .orElse("");
    }

    private static String combineUserMessages(List<ModelMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .filter(message -> "user".equalsIgnoreCase(message.role()))
                .map(ModelMessage::content)
                .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record PromptFingerprint(String systemSha256, String userSha256, String combined) {
    }
}
