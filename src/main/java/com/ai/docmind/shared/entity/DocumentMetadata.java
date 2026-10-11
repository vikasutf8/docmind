package com.ai.docmind.shared.entity;

import com.ai.docmind.shared.enums.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Postgres-backed metadata for one uploaded file, table
 * {@code document_metadata}. Stores metadata only — the binary lives on
 * disk/object storage (path managed by the service layer, not this row).
 *
 * <p>Supported today: {@code application/pdf}. {@code .doc}/{@code .docx}
 * are accepted next by extending {@link #SUPPORTED_CONTENT_TYPES} plus a
 * parser — no schema change needed.
 */
@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {

    /** Content types accepted by the upload path. doc/docx join this set later. */
    public static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("application/pdf");

    @Id
    @Column(name = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "filename", nullable = false, length = 512)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "total_chunks")
    private Integer totalChunks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** JPA only. */
    protected DocumentMetadata() {}

    private DocumentMetadata(
            UUID id,
            String filename,
            String contentType,
            long fileSize,
            DocumentStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.filename = filename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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
        Instant now = Instant.now();
        return new DocumentMetadata(
                UUID.randomUUID(), filename, contentType, fileSize, DocumentStatus.PENDING, now, now);
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
        if (status == null) {
            status = DocumentStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isReady() {
        return status == DocumentStatus.READY;
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
