package com.ai.docmind.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for dev profile.
 * Enabled via {@code app.cors.enabled=true} (default in dev).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CorsConfig {

    @Bean
    WebMvcConfigurer corsConfigurer(CorsProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(props.allowedOrigins().toArray(String[]::new))
                        .allowedMethods(props.allowedMethods().toArray(String[]::new))
                        .allowedHeaders(props.allowedHeaders().toArray(String[]::new))
                        .exposedHeaders(props.exposedHeaders().toArray(String[]::new))
                        .allowCredentials(props.allowCredentials())
                        .maxAge(props.maxAge());
            }
        };
    }
}