package com.example.legacyai;

import com.example.legacyai.application.DocumentIngestionService;
import com.example.legacyai.application.KnowledgeQueryService;
import com.example.legacyai.application.TextChunker;
import com.example.legacyai.config.AppConfig;
import com.example.legacyai.http.KnowledgeHttpServer;
import com.example.legacyai.infrastructure.ExtractiveChatClient;
import com.example.legacyai.infrastructure.FileKnowledgeRepository;
import com.example.legacyai.infrastructure.HashingEmbeddingClient;
import com.example.legacyai.infrastructure.OpenAiCompatibleChatClient;
import com.example.legacyai.infrastructure.OpenAiCompatibleEmbeddingClient;
import com.example.legacyai.ports.ChatClient;
import com.example.legacyai.ports.EmbeddingClient;
import com.example.legacyai.ports.KnowledgeRepository;

public final class LegacyAiApplication {
    private LegacyAiApplication() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        KnowledgeRepository repository = new FileKnowledgeRepository(config.storageDir());
        EmbeddingClient embeddingClient = embeddingClient(config);
        ChatClient chatClient = chatClient(config);

        DocumentIngestionService ingestionService = new DocumentIngestionService(
                repository,
                embeddingClient,
                new TextChunker(config.chunkMaxTokens(), config.chunkOverlapTokens())
        );
        KnowledgeQueryService queryService = new KnowledgeQueryService(repository, embeddingClient, chatClient);

        KnowledgeHttpServer server = new KnowledgeHttpServer(config, ingestionService, queryService, repository);
        server.start();

        System.out.printf(
                "Legacy AI knowledge-base service started on http://localhost:%d, provider=%s, storage=%s%n",
                config.port(),
                config.aiProvider(),
                config.storageDir().toAbsolutePath()
        );
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(3), "knowledge-base-shutdown"));
        server.awaitShutdown();
    }

    private static EmbeddingClient embeddingClient(AppConfig config) {
        if ("openai-compatible".equalsIgnoreCase(config.aiProvider())) {
            return new OpenAiCompatibleEmbeddingClient(
                    config.openAiBaseUrl(),
                    config.openAiApiKey(),
                    config.embeddingModel()
            );
        }
        return new HashingEmbeddingClient(config.localEmbeddingDimensions());
    }

    private static ChatClient chatClient(AppConfig config) {
        if ("openai-compatible".equalsIgnoreCase(config.aiProvider())) {
            return new OpenAiCompatibleChatClient(
                    config.openAiBaseUrl(),
                    config.openAiApiKey(),
                    config.chatModel()
            );
        }
        return new ExtractiveChatClient();
    }
}
