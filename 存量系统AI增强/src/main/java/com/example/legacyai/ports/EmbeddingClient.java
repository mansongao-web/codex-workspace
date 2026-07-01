package com.example.legacyai.ports;

public interface EmbeddingClient {
    double[] embed(String text);
}
