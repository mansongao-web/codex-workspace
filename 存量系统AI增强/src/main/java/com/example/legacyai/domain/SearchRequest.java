package com.example.legacyai.domain;

import java.util.Map;

public record SearchRequest(String query, int topK, double minScore, Map<String, String> filters) {
}
