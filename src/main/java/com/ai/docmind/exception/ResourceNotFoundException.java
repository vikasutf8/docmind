package com.ai.docmind.exception;

/** Thrown when a requested resource does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final String identifier;

    public ResourceNotFoundException(String resource, String identifier) {
        super("%s '%s' not found".formatted(resource, identifier));
        this.resource = resource;
        this.identifier = identifier;
    }

    public String resource() {
        return resource;
    }

    public String identifier() {
        return identifier;
    }
}
