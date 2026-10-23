package com.ai.docmind.chat.dtos;

import java.util.Map;
import java.util.UUID;

/** A citation referencing a document chunk used in a RAG answer. */
public record CitationDto(
        UUID documentId,
        String fileName,
        Integer chunkIndex,
        Integer pageNumber,
        String snippet,
        Double similarityScore,
        Map<String, Object> metadata) {
}