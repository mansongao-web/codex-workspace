package com.example.legacyai.domain;

import java.util.List;

public record SearchResult(String query, List<Citation> results) {
}
