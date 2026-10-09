package com.ai.docmind.config.provider.postgres;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL + pgvector provider. Only active with
 * {@code app.providers.pg.enabled=true}. Spring AI's PgVectorStore
 * (Phase 1) will build on this DataSource.
 */
@Configuration
@EnableConfigurationProperties(PostgresProperties.class)
@ConditionalOnProperty(prefix = "app.providers.pg", name = "enabled", havingValue = "true")
public class PostgresDataSourceConfig {

    @Bean("postgresDataSource")
    DataSource postgresDataSource(PostgresProperties props) {
        var ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(props.driverClassName())
                .url(props.url())
                .username(props.username())
                .password(props.password())
                .build();
        ds.setMaximumPoolSize(props.maximumPoolSize());
        ds.setMinimumIdle(props.minimumIdle());
        ds.setPoolName("pg-hikari");
        return ds;
    }

    @Bean("postgresJdbcTemplate")
    JdbcTemplate postgresJdbcTemplate(@Qualifier("postgresDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
