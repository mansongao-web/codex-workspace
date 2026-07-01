package com.example.legacyai.http;

import com.example.legacyai.application.DocumentIngestionService;
import com.example.legacyai.application.KnowledgeQueryService;
import com.example.legacyai.config.AppConfig;
import com.example.legacyai.domain.AnswerResponse;
import com.example.legacyai.domain.Citation;
import com.example.legacyai.domain.IngestionResult;
import com.example.legacyai.domain.KnowledgeChunk;
import com.example.legacyai.domain.KnowledgeDocument;
import com.example.legacyai.domain.SearchRequest;
import com.example.legacyai.domain.SearchResult;
import com.example.legacyai.ports.KnowledgeRepository;
import com.example.legacyai.util.JsonMaps;
import com.example.legacyai.util.MiniJson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class KnowledgeHttpServer {
    private final AppConfig config;
    private final DocumentIngestionService ingestionService;
    private final KnowledgeQueryService queryService;
    private final KnowledgeRepository repository;
    private final HttpServer server;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public KnowledgeHttpServer(
            AppConfig config,
            DocumentIngestionService ingestionService,
            KnowledgeQueryService queryService,
            KnowledgeRepository repository
    ) throws IOException {
        this.config = config;
        this.ingestionService = ingestionService;
        this.queryService = queryService;
        this.repository = repository;
        this.server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        this.server.createContext("/", this::route);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() {
        server.start();
    }

    public void stop(int delaySeconds) {
        server.stop(delaySeconds);
        shutdownLatch.countDown();
    }

    public void awaitShutdown() throws InterruptedException {
        shutdownLatch.await();
    }

    private void route(HttpExchange exchange) throws IOException {
        addDefaultHeaders(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            send(exchange, 204, "", "text/plain; charset=utf-8");
            return;
        }

        try {
            requireApiKey(exchange);
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("GET".equals(method) && "/".equals(path)) {
                handleIndex(exchange);
                return;
            }
            if ("GET".equals(method) && "/favicon.ico".equals(path)) {
                send(exchange, 204, "", "text/plain; charset=utf-8");
                return;
            }
            if ("GET".equals(method) && "/health".equals(path)) {
                sendJson(exchange, 200, Map.of("status", "UP"));
                return;
            }
            if ("GET".equals(method) && "/api/documents".equals(path)) {
                handleListDocuments(exchange);
                return;
            }
            if ("POST".equals(method) && "/api/documents".equals(path)) {
                handleIngestDocument(exchange);
                return;
            }
            if ("POST".equals(method) && "/api/faqs".equals(path)) {
                handleIngestFaq(exchange);
                return;
            }
            if ("POST".equals(method) && "/api/search".equals(path)) {
                handleSearch(exchange);
                return;
            }
            if ("POST".equals(method) && "/api/ask".equals(path)) {
                handleAsk(exchange);
                return;
            }
            if ("DELETE".equals(method) && path.startsWith("/api/documents/")) {
                handleDeleteDocument(exchange, path.substring("/api/documents/".length()));
                return;
            }

            sendJson(exchange, 404, Map.of("error", "Not Found"));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            sendJson(exchange, 401, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace(System.err);
            sendJson(exchange, 500, Map.of("error", e.getMessage() == null ? "Internal Server Error" : e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void handleListDocuments(HttpExchange exchange) throws IOException {
        Map<String, Long> chunkCounts = repository.listChunks().stream()
                .collect(Collectors.groupingBy(KnowledgeChunk::documentId, LinkedHashMap::new, Collectors.counting()));
        List<Map<String, Object>> documents = repository.listDocuments().stream()
                .map(document -> documentToMap(document, chunkCounts.getOrDefault(document.id(), 0L)))
                .toList();
        sendJson(exchange, 200, Map.of("documents", documents, "count", documents.size()));
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = KnowledgeHttpServer.class.getResourceAsStream("/static/index.html")) {
            if (inputStream == null) {
                send(exchange, 500, "Missing static/index.html", "text/plain; charset=utf-8");
                return;
            }
            String html = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            send(exchange, 200, html, "text/html; charset=utf-8");
        }
    }

    private void handleIngestDocument(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonObject(exchange);
        Map<String, String> metadata = metadataFrom(body);
        IngestionResult result = ingestionService.ingestDocument(
                JsonMaps.string(body, "title", ""),
                JsonMaps.string(body, "sourceUri", ""),
                JsonMaps.string(body, "sourceType", "DOCUMENT"),
                JsonMaps.string(body, "content", ""),
                metadata
        );
        sendJson(exchange, 201, Map.of("documentId", result.documentId(), "chunkCount", result.chunkCount()));
    }

    private void handleIngestFaq(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readJsonObject(exchange);
        IngestionResult result = ingestionService.ingestFaq(
                JsonMaps.string(body, "question", ""),
                JsonMaps.string(body, "answer", ""),
                JsonMaps.string(body, "sourceUri", ""),
                metadataFrom(body)
        );
        sendJson(exchange, 201, Map.of("documentId", result.documentId(), "chunkCount", result.chunkCount()));
    }

    private void handleSearch(HttpExchange exchange) throws IOException {
        SearchResult result = queryService.search(searchRequestFrom(readJsonObject(exchange)));
        sendJson(exchange, 200, Map.of(
                "query", result.query(),
                "results", result.results().stream().map(this::citationToMap).toList()
        ));
    }

    private void handleAsk(HttpExchange exchange) throws IOException {
        AnswerResponse response = queryService.ask(searchRequestFrom(readJsonObject(exchange)));
        sendJson(exchange, 200, Map.of(
                "answer", response.answer(),
                "generatedByModel", response.generatedByModel(),
                "citations", response.citations().stream().map(this::citationToMap).toList()
        ));
    }

    private void handleDeleteDocument(HttpExchange exchange, String rawId) throws IOException {
        String documentId = URLDecoder.decode(rawId, StandardCharsets.UTF_8);
        boolean deleted = repository.deleteDocument(documentId);
        sendJson(exchange, deleted ? 200 : 404, Map.of("deleted", deleted, "documentId", documentId));
    }

    private SearchRequest searchRequestFrom(Map<String, Object> body) {
        String query = JsonMaps.string(body, "query", "");
        if (query.isBlank()) {
            query = JsonMaps.string(body, "question", "");
        }
        return new SearchRequest(
                query,
                JsonMaps.integer(body, "topK", 5),
                JsonMaps.decimal(body, "minScore", -1.0),
                JsonMaps.stringMap(body, "filters")
        );
    }

    private Map<String, String> metadataFrom(Map<String, Object> body) {
        Map<String, String> metadata = JsonMaps.stringMap(body, "metadata");
        addIfPresent(metadata, "department", JsonMaps.string(body, "department", ""));
        addIfPresent(metadata, "category", JsonMaps.string(body, "category", ""));
        addIfPresent(metadata, "owner", JsonMaps.string(body, "owner", ""));
        List<String> tags = JsonMaps.stringList(body, "tags");
        if (!tags.isEmpty()) {
            metadata.put("tags", String.join(",", tags));
        }
        return metadata;
    }

    private static void addIfPresent(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private Map<String, Object> documentToMap(KnowledgeDocument document, long chunkCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", document.id());
        row.put("title", document.title());
        row.put("sourceUri", document.sourceUri());
        row.put("sourceType", document.sourceType());
        row.put("metadata", document.metadata());
        row.put("createdAt", document.createdAt().toString());
        row.put("contentHash", document.contentHash());
        row.put("chunkCount", chunkCount);
        return row;
    }

    private Map<String, Object> citationToMap(Citation citation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", citation.index());
        row.put("documentId", citation.documentId());
        row.put("chunkId", citation.chunkId());
        row.put("title", citation.title());
        row.put("sourceUri", citation.sourceUri());
        row.put("chunkOrdinal", citation.chunkOrdinal());
        row.put("score", citation.score());
        row.put("snippet", citation.snippet());
        return row;
    }

    private Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        String body = new String(bytes, StandardCharsets.UTF_8);
        if (body.isBlank()) {
            return new LinkedHashMap<>();
        }
        return JsonMaps.objectMap(MiniJson.parse(body));
    }

    private void requireApiKey(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (!config.apiKeyRequired()
                || "/".equals(path)
                || "/favicon.ico".equals(path)
                || "/health".equals(path)) {
            return;
        }
        String provided = exchange.getRequestHeaders().getFirst("X-KB-Api-Key");
        if (!config.apiKey().equals(provided)) {
            throw new SecurityException("Unauthorized");
        }
    }

    private void addDefaultHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-KB-Api-Key");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
    }

    private void sendJson(HttpExchange exchange, int status, Object value) throws IOException {
        send(exchange, status, MiniJson.stringify(value), "application/json; charset=utf-8");
    }

    private void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
