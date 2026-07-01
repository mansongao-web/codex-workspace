package com.example.legacyai.infrastructure;

import com.example.legacyai.ports.EmbeddingClient;
import com.example.legacyai.util.TextTokenizer;
import com.example.legacyai.util.VectorMath;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HashingEmbeddingClient implements EmbeddingClient {
    private final int dimensions;

    public HashingEmbeddingClient(int dimensions) {
        this.dimensions = Math.max(64, dimensions);
    }

    @Override
    public double[] embed(String text) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String term : TextTokenizer.terms(text)) {
            counts.merge(term, 1, Integer::sum);
        }

        double[] vector = new double[dimensions];
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int hash = entry.getKey().hashCode();
            int index = Math.floorMod(hash, dimensions);
            double sign = (Integer.rotateLeft(hash, 13) & 1) == 0 ? 1.0 : -1.0;
            vector[index] += sign * Math.sqrt(entry.getValue());
        }
        VectorMath.normalizeInPlace(vector);
        return vector;
    }
}
