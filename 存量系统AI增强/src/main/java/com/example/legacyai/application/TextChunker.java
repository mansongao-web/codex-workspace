package com.example.legacyai.application;

import com.example.legacyai.util.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

public final class TextChunker {
    private final int maxTokens;
    private final int overlapTokens;

    public TextChunker(int maxTokens, int overlapTokens) {
        this.maxTokens = Math.max(120, maxTokens);
        this.overlapTokens = Math.max(0, Math.min(overlapTokens, this.maxTokens / 3));
    }

    public List<String> chunk(String content) {
        String normalized = TextNormalizer.normalizeContent(content);
        if (normalized.isBlank()) {
            return List.of();
        }

        List<String> units = splitIntoUnits(normalized);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String unit : units) {
            if (estimateTokens(unit) > maxTokens) {
                flushCurrent(chunks, current);
                chunks.addAll(splitLongUnit(unit));
                continue;
            }

            int projectedTokens = estimateTokens(current + "\n" + unit);
            if (!current.isEmpty() && projectedTokens > maxTokens) {
                String emitted = current.toString().trim();
                chunks.add(emitted);
                current.setLength(0);
                appendOverlap(current, emitted);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(unit.trim());
        }

        flushCurrent(chunks, current);
        return chunks;
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int tokens = 0;
        boolean inAsciiWord = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isCjk(cp)) {
                tokens++;
                inAsciiWord = false;
            } else if (Character.isLetterOrDigit(cp)) {
                if (!inAsciiWord) {
                    tokens++;
                    inAsciiWord = true;
                }
            } else {
                inAsciiWord = false;
            }
            i += Character.charCount(cp);
        }
        return Math.max(1, tokens);
    }

    private static List<String> splitIntoUnits(String text) {
        List<String> units = new ArrayList<>();
        StringBuilder unit = new StringBuilder();
        int newlineRun = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            unit.appendCodePoint(cp);

            if (cp == '\n') {
                newlineRun++;
            } else if (!Character.isWhitespace(cp)) {
                newlineRun = 0;
            }

            if (newlineRun >= 2 || isSentenceEnd(cp)) {
                addUnit(units, unit);
                newlineRun = 0;
            }
            i += Character.charCount(cp);
        }
        addUnit(units, unit);
        return units;
    }

    private List<String> splitLongUnit(String unit) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < unit.length()) {
            int previousStart = start;
            int end = Math.min(unit.length(), start + maxTokens * 3);
            while (end < unit.length() && end > start && !Character.isWhitespace(unit.charAt(end - 1))) {
                end--;
            }
            if (end <= start) {
                end = Math.min(unit.length(), start + maxTokens * 3);
            }
            chunks.add(unit.substring(start, end).trim());
            start = end;
            if (overlapTokens > 0 && end < unit.length()) {
                start = Math.max(previousStart + 1, end - overlapTokens * 2);
            }
        }
        return chunks;
    }

    private void appendOverlap(StringBuilder target, String emitted) {
        if (overlapTokens == 0 || emitted.isBlank()) {
            return;
        }
        int approxChars = Math.min(emitted.length(), overlapTokens * 3);
        target.append(emitted.substring(emitted.length() - approxChars).trim());
    }

    private static void flushCurrent(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            String value = current.toString().trim();
            if (!value.isBlank()) {
                chunks.add(value);
            }
            current.setLength(0);
        }
    }

    private static void addUnit(List<String> units, StringBuilder unit) {
        String value = unit.toString().trim();
        if (!value.isBlank()) {
            units.add(value);
        }
        unit.setLength(0);
    }

    private static boolean isSentenceEnd(int cp) {
        return cp == '。' || cp == '！' || cp == '？' || cp == '；'
                || cp == '.' || cp == '!' || cp == '?' || cp == ';';
    }

    private static boolean isCjk(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
