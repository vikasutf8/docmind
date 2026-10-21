package com.ai.docmind.shared.entity;

import com.ai.docmind.shared.enums.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Postgres-backed metadata for one uploaded file, table
 * {@code document_metadata}. Metadata only — the binary lives on disk or
 * object storage, never in the row.
 *
 * <p>The lifecycle is encapsulated. {@code newUpload()} starts
 * {@code PENDING}; {@link #startProcessing()} moves to {@code PROCESSING};
 * the run ends in {@link #complete(int, int)} ({@code READY}, terminal) or
 * {@link #fail(String)} ({@code FAILED}, retryable). There are no public
 * setters, so callers cannot produce illegal transitions.
 *
 * <p>Supported today: {@code application/pdf}. {@code .doc}/{@code .docx}
 * join {@link #SUPPORTED_CONTENT_TYPES} with a parser later — no schema
 * change needed.
 */
@Entity
@Table(name = "document_metadata")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentMetadata {

    /** Content types accepted by the upload path. */
    public static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("application/pdf");

    private static final int ERROR_MESSAGE_LIMIT = 2000;

    /**
     * Client-assigned on purpose: the id must exist before the binary is
     * written, so the file name and the row stay in sync from the start.
     */
    @Id
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "filename", nullable = false, length = 512, updatable = false)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 120, updatable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    private DocumentMetadata(
            UUID id, String filename, String contentType, long fileSize, DocumentStatus status) {
        this.id = id;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.status = status;
    }

    /**
     * Factory for a fresh upload: status {@code PENDING}, page/chunk counts
     * null until extraction fills them in. Rejects blank names, negative
     * sizes and unsupported content types (pdf today).
     */
    public static DocumentMetadata newUpload(String filename, String contentType, long fileSize) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("unsupported contentType: " + contentType);
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must be >= 0");
        }
        return new DocumentMetadata(
                UUID.randomUUID(), filename.strip(), contentType, fileSize, DocumentStatus.PENDING);
    }

    /**
     * PENDING or FAILED -> PROCESSING (FAILED rows are retryable by design).
     * Clears the previous failure reason and any partial counts.
     */
    public void startProcessing() {
        if (status == DocumentStatus.PROCESSING || status == DocumentStatus.READY) {
            throw new IllegalStateException("cannot start processing from status " + status);
        }
        status = DocumentStatus.PROCESSING;
        totalPages = null;
        totalChunks = null;
        errorMessage = null;
    }

    /** PROCESSING -> READY. Records the extraction totals (both >= 0). */
    public void complete(int totalPages, int totalChunks) {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("complete is only allowed from PROCESSING, got " + status);
        }
        if (totalPages < 0 || totalChunks < 0) {
            throw new IllegalArgumentException("total pages/chunks must be >= 0");
        }
        this.totalPages = totalPages;
        this.totalChunks = totalChunks;
        status = DocumentStatus.READY;
    }

    /** Any state before READY -> FAILED with the persisted reason. */
    public void fail(String reason) {
        if (status == DocumentStatus.READY) {
            throw new IllegalStateException("READY cannot fail");
        }
        String message = reason == null || reason.isBlank() ? "unknown failure" : reason.strip();
        errorMessage = message.length() > ERROR_MESSAGE_LIMIT
                ? message.substring(0, ERROR_MESSAGE_LIMIT)
                : message;
        status = DocumentStatus.FAILED;
    }

    public boolean isReady() {
        return status == DocumentStatus.READY;
    }

    public boolean isFailed() {
        return status == DocumentStatus.FAILED;
    }
}
