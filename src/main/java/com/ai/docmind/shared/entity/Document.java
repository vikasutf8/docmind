package com.ai.docmind.shared.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared immutable document carrier. Plain record on purpose — no JPA/Mongo
 * starter yet, so no persistence annotations. Repository implementations
 * map to/from this type.
 */
public record Document(
        UUID id,
        String title,
        String content,
        String contentType,
        long sizeBytes,
        Instant createdAt,
        Instant updatedAt) {

    public Document {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("timestamps must not be null");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }

    /** Factory for new documents — generates id + timestamps. */
    public static Document newDocument(String title, String content, String contentType) {
        Instant now = Instant.now();
        String body = content == null ? "" : content;
        return new Document(
                UUID.randomUUID(),
                title,
                body,
                contentType == null ? "text/plain" : contentType,
                body.length(),
                now,
                now);
    }

    /** Returns a copy with replaced title/content and bumped updatedAt. */
    public Document withContent(String title, String content) {
        Instant now = Instant.now();
        String body = content == null ? this.content : content;
        return new Document(
                this.id,
                title == null ? this.title : title,
                body,
                this.contentType,
                body.length(),
                this.createdAt,
                now);
    }
}
