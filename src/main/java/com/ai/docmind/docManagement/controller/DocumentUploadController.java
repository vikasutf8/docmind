package com.ai.docmind.docManagement.controller;

import com.ai.docmind.constant.AppConstants;
import com.ai.docmind.docManagement.dtos.DocumentMetadataResponse;
import com.ai.docmind.docManagement.service.DocumentMetadataService;
import com.ai.docmind.exception.ProviderNotConfiguredException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Multipart upload endpoints for source documents. */
@RestController
@RequestMapping(AppConstants.Api.V1 + "/documents/uploads")
@Tag(name = "document-uploads", description = "Upload documents for indexing")
public class DocumentUploadController {

    private final ObjectProvider<DocumentMetadataService> service;

    public DocumentUploadController(ObjectProvider<DocumentMetadataService> service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and process a single document for indexing",
            description = "Uploads a document (PDF or other text formats), extracts text, "
                    + "chunks it, generates embeddings, and stores in the vector database. "
                    + "Returns the document metadata with ingestion status.")
    public ResponseEntity<DocumentMetadataResponse> upload(@RequestParam("file") MultipartFile file) {
        DocumentMetadataResponse response = requireService().uploadAndProcess(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and process multiple documents for indexing")
    public ResponseEntity<List<DocumentMetadataResponse>> uploadBatch(
            @RequestParam("files") List<MultipartFile> files) {
        List<DocumentMetadataResponse> results = new java.util.ArrayList<>(files.size());
        for (MultipartFile file : files) {
            results.add(requireService().uploadAndProcess(file));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    private DocumentMetadataService requireService() {
        DocumentMetadataService available = service.getIfAvailable();
        if (available == null) {
            throw new ProviderNotConfiguredException(AppConstants.Providers.POSTGRES);
        }
        return available;
    }
}
