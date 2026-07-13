package com.codeforge.ai.shared.util;

import java.util.List;

public final class MojibakeDetector {

    private static final List<String> KNOWN_BAD_FRAGMENTS = List.of(
            "閿熸枻鎷",
            "閿熸枻鎷?",
            "AI 閻",
            "缁?",
            "锟",
            "拷",
            "脙",
            "脗"
    );

    private MojibakeDetector() {}

    public static boolean containsQuestionMarkDamage(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains("???")) {
            return true;
        }

        int questionCount = 0;
        for (char current : text.toCharArray()) {
            if (current == '?') {
                questionCount++;
            }
        }
        if (questionCount >= 2 && questionCount * 1.0 / text.length() > 0.20) {
            return true;
        }
        return text.length() >= 8 && questionCount * 1.0 / text.length() > 0.35;
    }

    public static boolean containsMojibake(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (containsQuestionMarkDamage(text) || text.indexOf('\u0000') >= 0 || text.indexOf('\uFFFD') >= 0) {
            return true;
        }
        for (String fragment : KNOWN_BAD_FRAGMENTS) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
