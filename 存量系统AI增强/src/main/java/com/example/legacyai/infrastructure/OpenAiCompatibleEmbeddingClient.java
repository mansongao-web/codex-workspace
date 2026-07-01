package com.example.legacyai.infrastructure;

import com.example.legacyai.ports.EmbeddingClient;
import com.example.legacyai.util.JsonMaps;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OpenAiCompatibleEmbeddingClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public double[] embed(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", text);
        Map<String, Object> response = OpenAiCompatibleSupport.postJson(baseUrl, apiKey, "/v1/embeddings", body);

        Object dataValue = response.get("data");
        if (dataValue instanceof List<?> data && !data.isEmpty()) {
            Map<String, Object> first = JsonMaps.objectMap(data.get(0));
            Object embeddingValue = first.get("embedding");
            if (embeddingValue instanceof List<?> embeddingList) {
                double[] vector = new double[embeddingList.size()];
                for (int i = 0; i < embeddingList.size(); i++) {
                    Object item = embeddingList.get(i);
                    vector[i] = item instanceof Number number
                            ? number.doubleValue()
                            : Double.parseDouble(String.valueOf(item));
                }
                return vector;
            }
        }
        throw new IllegalStateException("Embedding response does not contain vector data");
    }
}
