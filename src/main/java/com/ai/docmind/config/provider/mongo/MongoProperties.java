package com.ai.docmind.config.provider.mongo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.providers.mongo.*}. Disabled by default. */
@ConfigurationProperties(prefix = "app.providers.mongo")
public record MongoProperties(boolean enabled, String connectionString, String database) {

    public MongoProperties {
        if (enabled && (connectionString == null || connectionString.isBlank())) {
            throw new IllegalArgumentException("app.providers.mongo.connection-string is required when enabled");
        }
    }
}
