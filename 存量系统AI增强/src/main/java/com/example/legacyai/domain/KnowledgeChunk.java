package com.example.legacyai.domain;

import java.time.Instant;
import java.util.Map;

public record KnowledgeChunk(
        String id,
        String documentId,
        String title,
        String sourceUri,
        String sourceType,
        int ordinal,
        String text,
        int tokenCount,
        double[] vector,
        Map<String, String> metadata,
        Instant createdAt
) {
}
