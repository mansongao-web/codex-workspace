package com.example.legacyai.config;

import java.nio.file.Path;

public record AppConfig(
        int port,
        Path storageDir,
        String aiProvider,
        int localEmbeddingDimensions,
        int chunkMaxTokens,
        int chunkOverlapTokens,
        String apiKey,
        String openAiBaseUrl,
        String openAiApiKey,
        String embeddingModel,
        String chatModel
) {
    public static AppConfig fromEnvironment() {
        return new AppConfig(
                intSetting("SERVER_PORT", 8080),
                Path.of(setting("KB_STORAGE_DIR", "data/knowledge-base")),
                setting("AI_PROVIDER", "local"),
                intSetting("LOCAL_EMBEDDING_DIMENSIONS", 384),
                intSetting("CHUNK_MAX_TOKENS", 520),
                intSetting("CHUNK_OVERLAP_TOKENS", 80),
                setting("KB_API_KEY", ""),
                stripTrailingSlash(setting("OPENAI_BASE_URL", "https://api.openai.com")),
                setting("OPENAI_API_KEY", ""),
                setting("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small"),
                setting("OPENAI_CHAT_MODEL", "gpt-4o-mini")
        );
    }

    public boolean apiKeyRequired() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static String setting(String key, String fallback) {
        String property = System.getProperty(key);
        if (property == null || property.isBlank()) {
            property = System.getProperty(key.toLowerCase().replace('_', '.'));
        }
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv(key);
        return env == null || env.isBlank() ? fallback : env;
    }

    private static int intSetting(String key, int fallback) {
        String value = setting(key, Integer.toString(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
