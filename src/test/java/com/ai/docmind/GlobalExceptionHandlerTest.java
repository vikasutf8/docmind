package com.ai.docmind;

import static org.assertj.core.api.Assertions.assertThat;

import com.ai.docmind.exception.BadRequestException;
import com.ai.docmind.exception.GlobalExceptionHandler;
import com.ai.docmind.exception.ProviderNotConfiguredException;
import com.ai.docmind.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFoundMapsTo404() {
        var problem = handler.handleNotFound(new ResourceNotFoundException("document", "abc"));
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).contains("document").contains("abc");
        assertThat(problem.getProperties()).containsEntry("resource", "document");
    }

    @Test
    void badRequestMapsTo400() {
        var problem = handler.handleBadRequest(new BadRequestException("chunk size must be positive"));
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("chunk size must be positive");
    }

    @Test
    void providerNotConfiguredMapsTo503() {
        var problem = handler.handleProviderNotConfigured(new ProviderNotConfiguredException("pg"));
        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getProperties()).containsEntry("provider", "pg");
    }

    @Test
    void fallbackMapsTo500() {
        var problem = handler.handleFallback(new IllegalStateException("boom"));
        assertThat(problem.getStatus()).isEqualTo(500);
    }
}
