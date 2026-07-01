package com.example.legacyai.domain;

public record Citation(
        int index,
        String documentId,
        String chunkId,
        String title,
        String sourceUri,
        int chunkOrdinal,
        double score,
        String snippet
) {
}
