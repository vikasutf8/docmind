package com.ai.docmind.config.provider.postgres;

import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA wiring for the pg provider only. Active with
 * {@code app.providers.pg.enabled=true}; otherwise the app boots with no
 * {@code EntityManagerFactory} (tests stay green without Docker).
 *
 * <p>Manual setup on purpose: provider DataSources are {@code @Qualifier}-
 * named with no {@code @Primary}, so Boot's JPA auto-config stays excluded
 * (see {@code application.yaml}) and this bean owns the pg persistence unit.
 * Scans {@code com.ai.docmind.shared.entity} where {@code DocumentMetadata}
 * lives.
 */
@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(prefix = "app.providers.pg", name = "enabled", havingValue = "true")
public class PostgresJpaConfig {

    @Bean("pgEntityManagerFactory")
    LocalContainerEntityManagerFactoryBean pgEntityManagerFactory(
            @Qualifier("postgresDataSource") DataSource ds) {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(ds);
        emf.setPackagesToScan("com.ai.docmind.shared.entity");
        emf.setPersistenceUnitName("pg");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.hbm2ddl.auto", "update");
        props.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        emf.setJpaPropertyMap(props);
        return emf;
    }

    @Bean("pgTransactionManager")
    PlatformTransactionManager pgTransactionManager(
            @Qualifier("pgEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
