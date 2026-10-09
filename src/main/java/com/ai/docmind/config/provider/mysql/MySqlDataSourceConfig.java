package com.ai.docmind.config.provider.mysql;

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
 * MySQL provider. Only active with {@code app.providers.mysql.enabled=true}.
 * Bean names are qualified — there is no {@code @Primary} DataSource.
 */
@Configuration
@EnableConfigurationProperties(MySqlProperties.class)
@ConditionalOnProperty(prefix = "app.providers.mysql", name = "enabled", havingValue = "true")
public class MySqlDataSourceConfig {

    @Bean("mysqlDataSource")
    DataSource mysqlDataSource(MySqlProperties props) {
        var ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .driverClassName(props.driverClassName())
                .url(props.url())
                .username(props.username())
                .password(props.password())
                .build();
        ds.setMaximumPoolSize(props.maximumPoolSize());
        ds.setMinimumIdle(props.minimumIdle());
        ds.setPoolName("mysql-hikari");
        return ds;
    }

    @Bean("mysqlJdbcTemplate")
    JdbcTemplate mysqlJdbcTemplate(@Qualifier("mysqlDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
