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
    }

    @Test
    void rejectsBadUploads() {
        assertThatThrownBy(() -> DocumentMetadata.newUpload(" ", "application/pdf", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentMetadata.newUpload("a.pdf", "application/msword", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentMetadata.newUpload("a.pdf", "application/pdf", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusTransitions() {
        var meta = DocumentMetadata.newUpload("paper.pdf", "application/pdf", 10);
        meta.setStatus(DocumentStatus.PROCESSING);
        meta.setTotalPages(3);
        meta.setTotalChunks(12);
        assertThat(meta.isReady()).isFalse();
        meta.setStatus(DocumentStatus.READY);
        assertThat(meta.isReady()).isTrue();
    }
}
