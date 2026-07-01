package com.example.legacyai.ports;

import com.example.legacyai.domain.KnowledgeChunk;
import com.example.legacyai.domain.KnowledgeDocument;

import java.util.List;
import java.util.Optional;

public interface KnowledgeRepository {
    List<KnowledgeDocument> listDocuments();

    Optional<KnowledgeDocument> findDocument(String documentId);

    List<KnowledgeChunk> listChunks();

    void upsert(KnowledgeDocument document, List<KnowledgeChunk> chunks);

    boolean deleteDocument(String documentId);
}
