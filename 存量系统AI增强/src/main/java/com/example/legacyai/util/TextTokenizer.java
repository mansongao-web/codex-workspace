package com.example.legacyai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TextTokenizer {
    private TextTokenizer() {
    }

    public static List<String> terms(String text) {
        List<String> base = baseTerms(text);
        List<String> output = new ArrayList<>(base);
        for (int i = 0; i + 1 < base.size(); i++) {
            output.add(base.get(i) + "_" + base.get(i + 1));
        }
        return output;
    }

    private static List<String> baseTerms(String text) {
        List<String> terms = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < normalized.length(); ) {
            int cp = normalized.codePointAt(i);
            if (isCjk(cp)) {
                flushAscii(terms, ascii);
                terms.add(new String(Character.toChars(cp)));
            } else if (Character.isLetterOrDigit(cp)) {
                ascii.appendCodePoint(cp);
            } else {
                flushAscii(terms, ascii);
            }
            i += Character.charCount(cp);
        }
        flushAscii(terms, ascii);
        return terms;
    }

    private static void flushAscii(List<String> terms, StringBuilder ascii) {
        if (!ascii.isEmpty()) {
            String value = ascii.toString();
            terms.add(value);
            if (value.length() > 5) {
                for (int i = 0; i + 3 <= value.length(); i++) {
                    terms.add(value.substring(i, i + 3));
                }
            }
            ascii.setLength(0);
        }
    }

    private static boolean isCjk(int cp) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }
}
