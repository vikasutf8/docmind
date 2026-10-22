package com.ai.docmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local file storage for uploaded source documents. */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String uploadDir) {

    public StorageProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            throw new IllegalArgumentException("app.storage.upload-dir must not be blank");
        }
    }
}
