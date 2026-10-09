package com.ai.docmind.config.provider.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MongoDB provider using the native sync driver (no Spring Data — the
 * client connects lazily, so nothing happens until first use).
 * Only active with {@code app.providers.mongo.enabled=true}.
 */
@Configuration
@EnableConfigurationProperties(MongoProperties.class)
@ConditionalOnProperty(prefix = "app.providers.mongo", name = "enabled", havingValue = "true")
public class MongoProviderConfig {

    @Bean(value = "mongoClient", destroyMethod = "close")
    MongoClient mongoClient(MongoProperties props) {
        return MongoClients.create(props.connectionString());
    }

    @Bean("mongoDatabaseName")
    String mongoDatabaseName(MongoProperties props) {
        return props.database();
    }
}
