package com.ai.docmind.config.provider.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import java.net.InetSocketAddress;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cassandra provider using the native DataStax driver (no Spring Data —
 * no eager session at startup). Only active with
 * {@code app.providers.cassandra.enabled=true}.
 */
@Configuration
@EnableConfigurationProperties(CassandraProperties.class)
@ConditionalOnProperty(prefix = "app.providers.cassandra", name = "enabled", havingValue = "true")
public class CassandraProviderConfig {

    @Bean(value = "cassandraSession", destroyMethod = "close")
    CqlSession cassandraSession(CassandraProperties props) {
        CqlSessionBuilder builder = CqlSession.builder();
        props.contactPoints()
                .forEach(host -> builder.addContactPoint(new InetSocketAddress(host, props.port())));
        builder.withLocalDatacenter(props.datacenter());
        if (props.username() != null && !props.username().isBlank()) {
            builder.withAuthCredentials(props.username(), props.password());
        }
        return builder.build();
    }
}
