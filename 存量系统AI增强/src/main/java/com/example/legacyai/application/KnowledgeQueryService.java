package com.example.legacyai.application;

import com.example.legacyai.domain.AnswerResponse;
import com.example.legacyai.domain.Citation;
import com.example.legacyai.domain.KnowledgeChunk;
import com.example.legacyai.domain.SearchRequest;
import com.example.legacyai.domain.SearchResult;
import com.example.legacyai.ports.ChatClient;
import com.example.legacyai.ports.EmbeddingClient;
import com.example.legacyai.ports.KnowledgeRepository;
import com.example.legacyai.util.TextNormalizer;
import com.example.legacyai.util.VectorMath;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class KnowledgeQueryService {
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final KnowledgeRepository repository;
    private final EmbeddingClient embeddingClient;
    private final ChatClient chatClient;

    public KnowledgeQueryService(
            KnowledgeRepository repository,
            EmbeddingClient embeddingClient,
            ChatClient chatClient
    ) {
        this.repository = repository;
        this.embeddingClient = embeddingClient;
        this.chatClient = chatClient;
    }

    public SearchResult search(SearchRequest request) {
        String query = requireQuery(request.query());
        int topK = normalizeTopK(request.topK());
        double minScore = request.minScore();
        Map<String, String> filters = request.filters() == null ? Map.of() : request.filters();
        double[] queryVector = embeddingClient.embed(query);

        AtomicInteger index = new AtomicInteger(1);
        List<Citation> citations = repository.listChunks().stream()
                .filter(chunk -> matchesFilters(chunk, filters))
                .map(chunk -> toScoredCitation(index.get(), chunk, VectorMath.cosine(queryVector, chunk.vector())))
                .filter(citation -> citation.score() >= minScore)
                .sorted(Comparator.comparingDouble(Citation::score).reversed())
                .limit(topK)
                .map(citation -> withIndex(citation, index.getAndIncrement()))
                .toList();

        return new SearchResult(query, citations);
    }

    public AnswerResponse ask(SearchRequest request) {
        SearchResult searchResult = search(request);
        String answer = chatClient.answer(searchResult.query(), searchResult.results());
        return new AnswerResponse(answer, searchResult.results(), chatClient.generatedByModel());
    }

    private static Citation toScoredCitation(int index, KnowledgeChunk chunk, double score) {
        return new Citation(
                index,
                chunk.documentId(),
                chunk.id(),
                chunk.title(),
                chunk.sourceUri(),
                chunk.ordinal(),
                score,
                TextNormalizer.snippet(chunk.text(), 420)
        );
    }

    private static Citation withIndex(Citation citation, int index) {
        return new Citation(
                index,
                citation.documentId(),
                citation.chunkId(),
                citation.title(),
                citation.sourceUri(),
                citation.chunkOrdinal(),
                citation.score(),
                citation.snippet()
        );
    }

    private static boolean matchesFilters(KnowledgeChunk chunk, Map<String, String> filters) {
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            String actual = chunk.metadata().get(filter.getKey());
            if (actual == null || !actual.equalsIgnoreCase(filter.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static int normalizeTopK(int topK) {
        if (topK <= 0) {
            return DEFAULT_TOP_K;
        }
        return Math.min(topK, MAX_TOP_K);
    }

    private static String requireQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        return query.trim();
    }
}
