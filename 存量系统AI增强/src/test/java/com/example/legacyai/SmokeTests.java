package com.example.legacyai;

import com.example.legacyai.application.DocumentIngestionService;
import com.example.legacyai.application.KnowledgeQueryService;
import com.example.legacyai.application.TextChunker;
import com.example.legacyai.domain.AnswerResponse;
import com.example.legacyai.domain.IngestionResult;
import com.example.legacyai.domain.SearchRequest;
import com.example.legacyai.domain.SearchResult;
import com.example.legacyai.infrastructure.ExtractiveChatClient;
import com.example.legacyai.infrastructure.FileKnowledgeRepository;
import com.example.legacyai.infrastructure.HashingEmbeddingClient;
import com.example.legacyai.ports.EmbeddingClient;
import com.example.legacyai.util.JsonMaps;
import com.example.legacyai.util.MiniJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public final class SmokeTests {
    public static void main(String[] args) throws Exception {
        jsonRoundTrip();
        ingestionSearchAskAndReload();
        System.out.println("Smoke tests passed.");
    }

    private static void jsonRoundTrip() {
        Map<String, Object> parsed = JsonMaps.objectMap(MiniJson.parse("{\"name\":\"报销\",\"tags\":[\"制度\",\"财务\"]}"));
        assertTrue("报销".equals(JsonMaps.string(parsed, "name", "")), "JSON parser should read string fields");
        assertTrue(JsonMaps.stringList(parsed, "tags").size() == 2, "JSON parser should read arrays");
        assertTrue(MiniJson.stringify(parsed).contains("报销"), "JSON writer should preserve text");
    }

    private static void ingestionSearchAskAndReload() throws Exception {
        Path temp = Files.createTempDirectory("legacy-ai-kb-test");
        try {
            FileKnowledgeRepository repository = new FileKnowledgeRepository(temp);
            EmbeddingClient embeddingClient = new HashingEmbeddingClient(256);
            DocumentIngestionService ingestionService = new DocumentIngestionService(
                    repository,
                    embeddingClient,
                    new TextChunker(120, 20)
            );
            KnowledgeQueryService queryService = new KnowledgeQueryService(
                    repository,
                    embeddingClient,
                    new ExtractiveChatClient()
            );

            IngestionResult policy = ingestionService.ingestDocument(
                    "差旅报销制度",
                    "policy://finance/travel",
                    "POLICY",
                    "员工差旅报销需要提交审批单、发票和行程单。住宿费按照城市等级执行标准，超标部分需部门负责人审批。",
                    Map.of("department", "财务", "category", "制度")
            );
            ingestionService.ingestFaq(
                    "CRM 客户资料如何查询？",
                    "进入 CRM 客户中心，通过客户名称、手机号或企业统一社会信用代码检索。",
                    "faq://crm/customer-search",
                    Map.of("department", "销售", "category", "FAQ")
            );

            SearchResult search = queryService.search(new SearchRequest("住宿报销需要什么材料", 3, -1.0, Map.of()));
            assertTrue(!search.results().isEmpty(), "search should return results");
            assertTrue(search.results().get(0).documentId().equals(policy.documentId()), "policy should be the top result");

            AnswerResponse answer = queryService.ask(new SearchRequest("差旅住宿报销怎么处理？", 3, -1.0, Map.of("department", "财务")));
            assertTrue(!answer.citations().isEmpty(), "answer should include citations");
            assertTrue(answer.answer().contains("[1]"), "answer should cite source index");

            FileKnowledgeRepository reloaded = new FileKnowledgeRepository(temp);
            assertTrue(reloaded.listDocuments().size() == 2, "repository should reload persisted documents");
            assertTrue(!reloaded.listChunks().isEmpty(), "repository should reload persisted chunks");
        } finally {
            deleteRecursively(temp);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
