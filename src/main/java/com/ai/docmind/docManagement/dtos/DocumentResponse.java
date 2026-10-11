package com.ai.docmind.docManagement.dtos;

import com.ai.docmind.shared.entity.Document;
import java.time.Instant;
import java.util.UUID;

/** Response body for document endpoints. Never leaks the entity directly. */
public record DocumentResponse(
        UUID id,
        String title,
        String content,
        String contentType,
        long sizeBytes,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.id(),
                doc.title(),
                doc.content(),
                doc.contentType(),
                doc.sizeBytes(),
                doc.createdAt(),
                doc.updatedAt());
    }
}
