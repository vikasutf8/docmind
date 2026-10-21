package com.ai.docmind.exception;

import java.util.Objects;
import java.util.UUID;

/**
 * Thrown when a stored document cannot be stored, parsed, chunked, embedded
 * or removed from the ingest pipeline. Carries the affected document id and
 * the failing stage so the global handler can return actionable context.
 */
public class DocumentProcessingException extends RuntimeException {

    /** Pipeline phase in which processing failed. */
    public enum Stage {
        STORAGE,
        EXTRACTION,
        CHUNKING,
        EMBEDDING,
        DELETION
    }

    private final UUID documentId;
    private final Stage stage;

    public DocumentProcessingException(UUID documentId, Stage stage, String message, Throwable cause) {
        super(message, cause);
        this.documentId = Objects.requireNonNull(documentId, "documentId must not be null");
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
    }

    public DocumentProcessingException(UUID documentId, Stage stage, String message) {
        this(documentId, stage, message, null);
    }

    public UUID documentId() {
        return documentId;
    }

    public Stage stage() {
        return stage;
    }
}
