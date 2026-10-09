package com.ai.docmind.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger / OpenAPI metadata. Served at /swagger-ui.html and /v3/api-docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI docmindOpenApi(
            AppProperties props,
            @Value("${server.port:8080}") int port) {
        return new OpenAPI()
                .info(new Info()
                        .title(props.name())
                        .version(props.version())
                        .description(props.description())
                        .contact(new Contact().name("docmind team")))
                .servers(List.of(new Server().url("http://localhost:" + port)));
    }
}
