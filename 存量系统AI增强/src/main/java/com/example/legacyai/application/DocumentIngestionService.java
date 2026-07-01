package com.example.legacyai.application;

import com.example.legacyai.domain.IngestionResult;
import com.example.legacyai.domain.KnowledgeChunk;
import com.example.legacyai.domain.KnowledgeDocument;
import com.example.legacyai.ports.EmbeddingClient;
import com.example.legacyai.ports.KnowledgeRepository;
import com.example.legacyai.util.ContentHash;
import com.example.legacyai.util.TextNormalizer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DocumentIngestionService {
    private final KnowledgeRepository repository;
    private final EmbeddingClient embeddingClient;
    private final TextChunker chunker;

    public DocumentIngestionService(
            KnowledgeRepository repository,
            EmbeddingClient embeddingClient,
            TextChunker chunker
    ) {
        this.repository = repository;
        this.embeddingClient = embeddingClient;
        this.chunker = chunker;
    }

    public IngestionResult ingestDocument(
            String title,
            String sourceUri,
            String sourceType,
            String content,
            Map<String, String> metadata
    ) {
        String normalizedContent = TextNormalizer.normalizeContent(required(content, "content"));
        String normalizedTitle = TextNormalizer.singleLine(required(title, "title"));
        String normalizedSourceType = sourceType == null || sourceType.isBlank()
                ? "DOCUMENT"
                : TextNormalizer.singleLine(sourceType).toUpperCase();
        String id = stableDocumentId(normalizedSourceType, normalizedTitle, sourceUri, normalizedContent);
        Instant now = Instant.now();
        Map<String, String> safeMetadata = metadata == null ? Map.of() : new LinkedHashMap<>(metadata);

        KnowledgeDocument document = new KnowledgeDocument(
                id,
                normalizedTitle,
                sourceUri == null ? "" : sourceUri.trim(),
                normalizedSourceType,
                safeMetadata,
                now,
                ContentHash.sha256(normalizedContent)
        );

        List<String> texts = chunker.chunk(normalizedContent);
        List<KnowledgeChunk> chunks = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String chunkText = texts.get(i);
            chunks.add(new KnowledgeChunk(
                    id + "#chunk-" + (i + 1),
                    id,
                    normalizedTitle,
                    document.sourceUri(),
                    normalizedSourceType,
                    i + 1,
                    chunkText,
                    TextChunker.estimateTokens(chunkText),
                    embeddingClient.embed(chunkText),
                    safeMetadata,
                    now
            ));
        }

        repository.upsert(document, chunks);
        return new IngestionResult(document.id(), chunks.size());
    }

    public IngestionResult ingestFaq(
            String question,
            String answer,
            String sourceUri,
            Map<String, String> metadata
    ) {
        String q = required(question, "question");
        String a = required(answer, "answer");
        Map<String, String> enriched = new LinkedHashMap<>();
        if (metadata != null) {
            enriched.putAll(metadata);
        }
        enriched.put("faqQuestion", TextNormalizer.singleLine(q));
        return ingestDocument(
                TextNormalizer.singleLine(q),
                sourceUri,
                "FAQ",
                "问题：" + q + "\n答案：" + a,
                enriched
        );
    }

    private static String stableDocumentId(String sourceType, String title, String sourceUri, String content) {
        String seed = sourceType + "|" + title + "|" + (sourceUri == null ? "" : sourceUri) + "|" + ContentHash.sha256(content);
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
