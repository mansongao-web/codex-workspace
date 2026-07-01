package com.example.legacyai.domain;

import java.util.List;

public record AnswerResponse(String answer, List<Citation> citations, boolean generatedByModel) {
}
