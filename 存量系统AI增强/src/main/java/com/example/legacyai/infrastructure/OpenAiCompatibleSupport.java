package com.example.legacyai.infrastructure;

import com.example.legacyai.util.JsonMaps;
import com.example.legacyai.util.MiniJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class OpenAiCompatibleSupport {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private OpenAiCompatibleSupport() {
    }

    static Map<String, Object> postJson(String baseUrl, String apiKey, String path, Map<String, Object> body) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required when AI_PROVIDER=openai-compatible");
        }
        String payload = MiniJson.stringify(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI provider returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return JsonMaps.objectMap(MiniJson.parse(response.body()));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call AI provider", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI provider request interrupted", e);
        }
    }
}
