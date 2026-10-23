package com.ai.docmind.chat.dtos;

import java.util.List;

/** Response from the RAG system containing answer and citations. */
public record ChatResponseDto(
        String answer,
        String conversationId,
        List<CitationDto> citations,
        Long responseTimeMs) {
}