package com.ai.docmind.docManagement.controller;

import com.ai.docmind.constant.AppConstants;
import com.ai.docmind.docManagement.dtos.CreateDocumentRequest;
import com.ai.docmind.docManagement.dtos.DocumentResponse;
import com.ai.docmind.docManagement.dtos.UpdateDocumentRequest;
import com.ai.docmind.docManagement.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CRUD endpoints for managed documents. Errors map via GlobalExceptionHandler. */
@RestController
@RequestMapping(AppConstants.Api.V1 + "/documents")
@Tag(name = "documents", description = "Document management CRUD")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a document")
    public ResponseEntity<DocumentResponse> create(@Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse created = service.create(request);
        return ResponseEntity.created(URI.create(
                        AppConstants.Api.V1 + "/documents/" + created.id()))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List all documents")
    public List<DocumentResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a document by id")
    public DocumentResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a document")
    public DocumentResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
