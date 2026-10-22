package com.ai.docmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.docmind.shared.entity.DocumentMetadata;
import com.ai.docmind.shared.enums.DocumentStatus;
import org.junit.jupiter.api.Test;

class DocumentMetadataTest {

    @Test
    void newUploadDefaultsToPending() {
        var meta = DocumentMetadata.newUpload("paper.pdf", "application/pdf", 1024);

        assertThat(meta.getId()).isNotNull();
        assertThat(meta.getFilename()).isEqualTo("paper.pdf");
        assertThat(meta.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(meta.getTotalPages()).isNull();
        assertThat(meta.getTotalChunks()).isNull();
        assertThat(meta.isReady()).isFalse();
        assertThat(meta.isFailed()).isFalse();
    }

    @Test
    void rejectsBadUploads() {
        assertThatThrownBy(() -> DocumentMetadata.newUpload(" ", "application/pdf", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentMetadata.newUpload("a.pdf", "application/x-unsupported", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentMetadata.newUpload("a.pdf", "application/pdf", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processingRunReachesReady() {
        var meta = DocumentMetadata.newUpload("paper.pdf", "application/pdf", 10);

        meta.startProcessing();
        meta.complete(3, 12);

        assertThat(meta.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(meta.getTotalPages()).isEqualTo(3);
        assertThat(meta.getTotalChunks()).isEqualTo(12);
        assertThat(meta.isReady()).isTrue();
        assertThat(meta.getErrorMessage()).isNull();
    }

    @Test
    void failureIsRetryable() {
        var meta = DocumentMetadata.newUpload("paper.pdf", "application/pdf", 10);

        meta.fail("tokenizer offline");
        assertThat(meta.isFailed()).isTrue();
        assertThat(meta.getErrorMessage()).isEqualTo("tokenizer offline");

        meta.startProcessing();
        assertThat(meta.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(meta.getErrorMessage()).isNull();
    }

    @Test
    void rejectsIllegalTransitions() {
        var meta = DocumentMetadata.newUpload("paper.pdf", "application/pdf", 10);

        assertThatThrownBy(() -> meta.complete(1, 1))
                .isInstanceOf(IllegalStateException.class);

        meta.startProcessing();
        assertThatThrownBy(() -> meta.startProcessing())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> meta.complete(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);

        meta.complete(3, 12);
        assertThatThrownBy(() -> meta.startProcessing())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> meta.fail("late"))
                .isInstanceOf(IllegalStateException.class);
    }
}
