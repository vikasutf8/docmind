package com.ai.docmind.config.provider.cassandra;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code app.providers.cassandra.*}. Disabled by default. */
@ConfigurationProperties(prefix = "app.providers.cassandra")
public record CassandraProperties(
        boolean enabled,
        List<String> contactPoints,
        int port,
        String datacenter,
        String username,
        String password) {

    public CassandraProperties {
        if (enabled && (contactPoints == null || contactPoints.isEmpty())) {
            throw new IllegalArgumentException(
                    "app.providers.cassandra.contact-points is required when enabled");
        }
        if (datacenter == null || datacenter.isBlank()) {
            datacenter = "datacenter1";
        }
    }
}
