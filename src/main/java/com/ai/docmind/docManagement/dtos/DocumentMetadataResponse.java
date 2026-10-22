package com.ai.docmind.docManagement.dtos;

import com.ai.docmind.shared.entity.DocumentMetadata;
import com.ai.docmind.shared.enums.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

/** API view of an uploaded document's ingestion state. */
public record DocumentMetadataResponse(
        UUID id,
        String filename,
        String contentType,
        long fileSize,
        Integer totalPages,
        Integer totalChunks,
        DocumentStatus status,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentMetadataResponse from(DocumentMetadata metadata) {
        return new DocumentMetadataResponse(
                metadata.getId(),
                metadata.getFilename(),
                metadata.getContentType(),
                metadata.getFileSize(),
                metadata.getTotalPages(),
                metadata.getTotalChunks(),
                metadata.getStatus(),
                metadata.getErrorMessage(),
                metadata.getCreatedAt(),
                metadata.getUpdatedAt());
    }
}
