package com.ai.docmind.chat.dtos;

import java.util.UUID;

/** Request for asking a question to the RAG system. */
public record ChatRequestDto(
        String question,
        UUID documentId,
        Integer topK,
        Double minSimilarity,
        String conversationId) {

    public ChatRequestDto {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
    }
}