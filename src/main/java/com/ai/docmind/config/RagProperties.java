package com.ai.docmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG tuning knobs bound from {@code app.rag.*}. Always active (pure values,
 * no connections) — used by the Phase 1 chunk/embed/retrieve pipeline.
 */
@ConfigurationProperties(prefix = "app.rag")
public record RagProperties(int chunkSize, int chunkOverlap, int topK, double similarityThreshold) {

    public RagProperties {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("app.rag.chunk-size must be positive");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("app.rag.chunk-overlap must be in [0, chunk-size)");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("app.rag.top-k must be positive");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("app.rag.similarity-threshold must be in [0, 1]");
        }
    }
}
