package com.ai.docmind.exception;

/**
 * Thrown when code touches a provider whose {@code app.providers.*.enabled}
 * flag is off. Maps to HTTP 503 so callers know it is configuration,
 * not their request, that is wrong.
 */
public class ProviderNotConfiguredException extends RuntimeException {

    private final String provider;

    public ProviderNotConfiguredException(String provider) {
        super("provider '%s' is not enabled (app.providers.%s.enabled=true)".formatted(provider, provider));
        this.provider = provider;
    }

    public String provider() {
        return provider;
    }
}
