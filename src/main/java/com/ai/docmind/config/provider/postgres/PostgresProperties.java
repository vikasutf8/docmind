package com.ai.docmind.config.provider.postgres;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.providers.pg.*}. This is the primary AI store
 * (PostgreSQL + pgvector). Disabled by default.
 */
@ConfigurationProperties(prefix = "app.providers.pg")
public record PostgresProperties(
        boolean enabled,
        String url,
        String username,
        String password,
        String driverClassName,
        int maximumPoolSize,
        int minimumIdle,
        Vector vector) {

    public PostgresProperties {
        if (enabled && (url == null || url.isBlank())) {
            throw new IllegalArgumentException("app.providers.pg.url is required when enabled");
        }
        if (vector == null) {
            vector = new Vector("vector_store", 1536, true);
        }
    }

    public record Vector(String tableName, int dimensions, boolean initializeSchema) {}
}
