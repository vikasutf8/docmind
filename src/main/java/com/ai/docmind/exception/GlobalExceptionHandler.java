package com.ai.docmind.exception;

import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single place for API error mapping. Returns RFC 9457 {@link ProblemDetail}
 * so every error has the same shape: type/status/title/detail/instance.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        problem.setProperty("resource", ex.resource());
        problem.setProperty("identifier", ex.identifier());
        return problem;
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad request");
        return problem;
    }

    @ExceptionHandler(ProviderNotConfiguredException.class)
    public ProblemDetail handleProviderNotConfigured(ProviderNotConfiguredException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Provider not configured");
        problem.setProperty("provider", ex.provider());
        return problem;
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ProblemDetail handleDocumentProcessing(DocumentProcessingException ex) {
        HttpStatus status = statusFor(ex.stage());
        log.warn(
                "Document processing failed (documentId={}, stage={}): {}",
                ex.documentId(),
                ex.stage(),
                ex.getMessage(),
                ex);
        var problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("Document processing failed");
        problem.setProperty("documentId", ex.documentId());
        problem.setProperty("stage", ex.stage().name());
        return problem;
    }

    private static HttpStatus statusFor(DocumentProcessingException.Stage stage) {
        return switch (stage) {
            case EXTRACTION, CHUNKING -> HttpStatus.UNPROCESSABLE_CONTENT;
            case STORAGE, EMBEDDING, DELETION -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getField(),
                        e -> e.getDefaultMessage() == null ? "invalid" : e.getDefaultMessage(),
                        (first, second) -> first));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "validation failed");
        problem.setTitle("Bad request");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleFallback(Exception ex) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "unexpected error");
        problem.setTitle("Internal server error");
        return problem;
    }
}
