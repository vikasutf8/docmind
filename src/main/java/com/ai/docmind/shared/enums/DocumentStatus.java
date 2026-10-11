package com.ai.docmind.shared.enums;

/**
 * Lifecycle of a document's ingest pipeline. Stored as {@code VARCHAR} via
 * {@code @Enumerated(STRING)} so values are readable in SQL and append-only:
 * never rename or reorder, only add new constants at the end.
 */
public enum DocumentStatus {

    /** Row created on upload; chunking/embedding not started yet. */
    PENDING,

    /** Worker is extracting pages, chunking and embedding. */
    PROCESSING,

    /** All chunks embedded and searchable. Terminal success state. */
    READY,

    /** Pipeline failed; see {@code DocumentMetadata.errorMessage}. Retryable. */
    FAILED
}
