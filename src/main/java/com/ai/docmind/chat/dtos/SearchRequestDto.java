package com.ai.docmind.chat.dtos;

import java.util.UUID;

/** Request for searching similar chunks in the vector store. */
public record SearchRequestDto(
        String query,
        UUID documentId,
        Integer topK,
        Double minSimilarity) {

    public SearchRequestDto {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
    }
}