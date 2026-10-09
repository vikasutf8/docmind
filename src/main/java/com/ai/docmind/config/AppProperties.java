package com.ai.docmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root {@code app.*} properties. Provider blocks live in their own
 * {@code app.providers.*} classes; this holds shared metadata only.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(String name, String version, String description) {

    public AppProperties {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("app.name must not be blank");
        }
    }
}
