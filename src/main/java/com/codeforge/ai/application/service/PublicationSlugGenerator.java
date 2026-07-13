package com.codeforge.ai.application.service;

import java.security.SecureRandom;
import java.util.Locale;

public final class PublicationSlugGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_BASE_LENGTH = 48;
    private static final int SUFFIX_LENGTH = 6;
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789";

    private PublicationSlugGenerator() {
    }

    public static String generateSlug(String publicTitle) {
        String base = slugify(publicTitle);
        if (base.isBlank()) {
            base = "app";
        }
        return base + "-" + randomSuffix();
    }

    static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5\\s-]", "")
                .replaceAll("[\\s_]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank() || normalized.chars().allMatch(ch -> ch == '-'
                || (ch >= '\u4e00' && ch <= '\u9fa5'))) {
            return "";
        }
        String asciiOnly = normalized.replaceAll("[^a-z0-9-]", "");
        if (asciiOnly.length() > MAX_BASE_LENGTH) {
            asciiOnly = asciiOnly.substring(0, MAX_BASE_LENGTH).replaceAll("-+$", "");
        }
        return asciiOnly;
    }

    private static String randomSuffix() {
        StringBuilder builder = new StringBuilder(SUFFIX_LENGTH);
        for (int index = 0; index < SUFFIX_LENGTH; index++) {
            builder.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return builder.toString();
    }
}
