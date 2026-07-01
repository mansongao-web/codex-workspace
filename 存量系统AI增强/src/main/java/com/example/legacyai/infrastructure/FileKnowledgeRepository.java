package com.example.legacyai.infrastructure;

import com.example.legacyai.domain.KnowledgeChunk;
import com.example.legacyai.domain.KnowledgeDocument;
import com.example.legacyai.ports.KnowledgeRepository;
import com.example.legacyai.util.JsonMaps;
import com.example.legacyai.util.MiniJson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class FileKnowledgeRepository implements KnowledgeRepository {
    private final Path storageDir;
    private final Path documentsFile;
    private final Path chunksFile;
    private final Map<String, KnowledgeDocument> documents = new LinkedHashMap<>();
    private final Map<String, List<KnowledgeChunk>> chunksByDocument = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public FileKnowledgeRepository(Path storageDir) throws IOException {
        this.storageDir = storageDir;
        this.documentsFile = storageDir.resolve("documents.jsonl");
        this.chunksFile = storageDir.resolve("chunks.jsonl");
        Files.createDirectories(storageDir);
        load();
    }

    @Override
    public List<KnowledgeDocument> listDocuments() {
        lock.readLock().lock();
        try {
            return documents.values().stream()
                    .sorted(Comparator.comparing(KnowledgeDocument::createdAt).reversed())
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<KnowledgeDocument> findDocument(String documentId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(documents.get(documentId));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<KnowledgeChunk> listChunks() {
        lock.readLock().lock();
        try {
            return chunksByDocument.values().stream()
                    .flatMap(List::stream)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void upsert(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        lock.writeLock().lock();
        try {
            documents.put(document.id(), document);
            chunksByDocument.put(document.id(), List.copyOf(chunks));
            persist();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist knowledge base", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean deleteDocument(String documentId) {
        lock.writeLock().lock();
        try {
            boolean removed = documents.remove(documentId) != null;
            chunksByDocument.remove(documentId);
            if (removed) {
                persist();
            }
            return removed;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist knowledge base", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void load() throws IOException {
        lock.writeLock().lock();
        try {
            documents.clear();
            chunksByDocument.clear();
            if (Files.exists(documentsFile)) {
                try (BufferedReader reader = Files.newBufferedReader(documentsFile, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            KnowledgeDocument document = documentFromJson(JsonMaps.objectMap(MiniJson.parse(line)));
                            documents.put(document.id(), document);
                        }
                    }
                }
            }
            if (Files.exists(chunksFile)) {
                try (BufferedReader reader = Files.newBufferedReader(chunksFile, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            KnowledgeChunk chunk = chunkFromJson(JsonMaps.objectMap(MiniJson.parse(line)));
                            chunksByDocument.computeIfAbsent(chunk.documentId(), ignored -> new ArrayList<>()).add(chunk);
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void persist() throws IOException {
        writeJsonLinesAtomically(documentsFile, documents.values().stream().map(this::documentToJson).toList());
        List<Map<String, Object>> chunkRows = chunksByDocument.values().stream()
                .flatMap(List::stream)
                .map(this::chunkToJson)
                .toList();
        writeJsonLinesAtomically(chunksFile, chunkRows);
    }

    private void writeJsonLinesAtomically(Path target, List<Map<String, Object>> rows) throws IOException {
        Path temp = storageDir.resolve(target.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            for (Map<String, Object> row : rows) {
                writer.write(MiniJson.stringify(row));
                writer.newLine();
            }
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailed) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Map<String, Object> documentToJson(KnowledgeDocument document) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", document.id());
        row.put("title", document.title());
        row.put("sourceUri", document.sourceUri());
        row.put("sourceType", document.sourceType());
        row.put("metadata", document.metadata());
        row.put("createdAt", document.createdAt().toString());
        row.put("contentHash", document.contentHash());
        return row;
    }

    private Map<String, Object> chunkToJson(KnowledgeChunk chunk) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", chunk.id());
        row.put("documentId", chunk.documentId());
        row.put("title", chunk.title());
        row.put("sourceUri", chunk.sourceUri());
        row.put("sourceType", chunk.sourceType());
        row.put("ordinal", chunk.ordinal());
        row.put("text", chunk.text());
        row.put("tokenCount", chunk.tokenCount());
        row.put("vector", chunk.vector());
        row.put("metadata", chunk.metadata());
        row.put("createdAt", chunk.createdAt().toString());
        return row;
    }

    private static KnowledgeDocument documentFromJson(Map<String, Object> row) {
        return new KnowledgeDocument(
                JsonMaps.string(row, "id", ""),
                JsonMaps.string(row, "title", ""),
                JsonMaps.string(row, "sourceUri", ""),
                JsonMaps.string(row, "sourceType", "DOCUMENT"),
                JsonMaps.stringMap(row, "metadata"),
                Instant.parse(JsonMaps.string(row, "createdAt", Instant.EPOCH.toString())),
                JsonMaps.string(row, "contentHash", "")
        );
    }

    private static KnowledgeChunk chunkFromJson(Map<String, Object> row) {
        return new KnowledgeChunk(
                JsonMaps.string(row, "id", ""),
                JsonMaps.string(row, "documentId", ""),
                JsonMaps.string(row, "title", ""),
                JsonMaps.string(row, "sourceUri", ""),
                JsonMaps.string(row, "sourceType", "DOCUMENT"),
                JsonMaps.integer(row, "ordinal", 0),
                JsonMaps.string(row, "text", ""),
                JsonMaps.integer(row, "tokenCount", 0),
                vectorFromJson(row.get("vector")),
                JsonMaps.stringMap(row, "metadata"),
                Instant.parse(JsonMaps.string(row, "createdAt", Instant.EPOCH.toString()))
        );
    }

    private static double[] vectorFromJson(Object value) {
        if (!(value instanceof List<?> list)) {
            return new double[0];
        }
        double[] vector = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            vector[i] = item instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(item));
        }
        return vector;
    }
}
