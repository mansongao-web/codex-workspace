package com.example.legacyai.ports;

import com.example.legacyai.domain.Citation;

import java.util.List;

public interface ChatClient {
    String answer(String question, List<Citation> citations);

    boolean generatedByModel();
}
