package com.ai.docmind.config.provider.mysql;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.providers.mysql.*}. Disabled by default. */
@ConfigurationProperties(prefix = "app.providers.mysql")
public record MySqlProperties(
        boolean enabled,
        String url,
        String username,
        String password,
        String driverClassName,
        int maximumPoolSize,
        int minimumIdle) {

    public MySqlProperties {
        if (enabled && (url == null || url.isBlank())) {
            throw new IllegalArgumentException("app.providers.mysql.url is required when enabled");
        }
    }
}
