package com.example.legacyai.infrastructure;

import com.example.legacyai.domain.Citation;
import com.example.legacyai.ports.ChatClient;
import com.example.legacyai.util.JsonMaps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleChatClient implements ChatClient {
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleChatClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String answer(String question, List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "未在知识库中检索到足够相关的资料。";
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("messages", List.of(
                message("system", """
                        你是企业知识库问答助手。只能基于给定资料回答；无法从资料确认时要说明“知识库未提供相关依据”。
                        回答要专业、简洁，并在关键结论后使用 [1]、[2] 这样的引用编号。
                        """),
                message("user", prompt(question, citations))
        ));

        Map<String, Object> response = OpenAiCompatibleSupport.postJson(baseUrl, apiKey, "/v1/chat/completions", body);
        Object choicesValue = response.get("choices");
        if (choicesValue instanceof List<?> choices && !choices.isEmpty()) {
            Map<String, Object> first = JsonMaps.objectMap(choices.get(0));
            Map<String, Object> message = JsonMaps.objectMap(first.get("message"));
            return JsonMaps.string(message, "content", "").trim();
        }
        throw new IllegalStateException("Chat response does not contain choices");
    }

    @Override
    public boolean generatedByModel() {
        return true;
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String prompt(String question, List<Citation> citations) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户问题：").append(question).append("\n\n知识库资料：\n");
        List<Citation> limited = new ArrayList<>(citations.subList(0, Math.min(8, citations.size())));
        for (Citation citation : limited) {
            prompt.append("[")
                    .append(citation.index())
                    .append("] ")
                    .append(citation.title())
                    .append(" / 段落 ")
                    .append(citation.chunkOrdinal());
            if (citation.sourceUri() != null && !citation.sourceUri().isBlank()) {
                prompt.append(" / ").append(citation.sourceUri());
            }
            prompt.append("\n")
                    .append(citation.snippet())
                    .append("\n\n");
        }
        prompt.append("请基于以上资料回答。");
        return prompt.toString();
    }
}
