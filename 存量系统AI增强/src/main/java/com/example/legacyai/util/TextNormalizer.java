package com.example.legacyai.util;

public final class TextNormalizer {
    private TextNormalizer() {
    }

    public static String normalizeContent(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00a0', ' ')
                .trim();
    }

    public static String singleLine(String text) {
        return normalizeContent(text).replaceAll("\\s+", " ").trim();
    }

    public static String snippet(String text, int maxChars) {
        String value = singleLine(text);
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 1)).trim() + "…";
    }
}
