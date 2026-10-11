package com.ai.docmind.docManagement.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for POST /api/v1/documents. */
public record CreateDocumentRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 255, message = "title must be <= 255 chars")
        String title,

        @NotBlank(message = "content must not be blank")
        String content,

        @Size(max = 120, message = "contentType must be <= 120 chars")
        String contentType) {
}
