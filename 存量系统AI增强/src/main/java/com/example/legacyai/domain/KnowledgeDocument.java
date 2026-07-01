package com.example.legacyai.domain;

import java.time.Instant;
import java.util.Map;

public record KnowledgeDocument(
        String id,
        String title,
        String sourceUri,
        String sourceType,
        Map<String, String> metadata,
        Instant createdAt,
        String contentHash
) {
}
