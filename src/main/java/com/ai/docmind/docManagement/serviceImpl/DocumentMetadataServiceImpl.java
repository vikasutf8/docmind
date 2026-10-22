package com.ai.docmind.docManagement.serviceImpl;

import com.ai.docmind.config.RagProperties;
import com.ai.docmind.docManagement.dtos.DocumentMetadataResponse;
import com.ai.docmind.docManagement.repository.DocumentMetadataRepository;
import com.ai.docmind.docManagement.service.DocumentIngestService;
import com.ai.docmind.docManagement.service.DocumentMetadataService;
import com.ai.docmind.docManagement.service.DocumentParsingService;
import com.ai.docmind.exception.DocumentProcessingException;
import com.ai.docmind.exception.ProviderNotConfiguredException;
import com.ai.docmind.shared.entity.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** Orchestrates upload, parsing and ingestion for documents. */
@Service
@ConditionalOnProperty(prefix = "app.providers.pg", name = "enabled", havingValue = "true")
public class DocumentMetadataServiceImpl implements DocumentMetadataService {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataServiceImpl.class);

    private final DocumentMetadataRepository metadataRepository;
    private final DocumentParsingService parsingService;
    private final ObjectProvider<DocumentIngestService> ingestionServiceProvider;
    private final RagProperties ragProperties;

    public DocumentMetadataServiceImpl(DocumentMetadataRepository metadataRepository,
                                        DocumentParsingService parsingService,
                                        ObjectProvider<DocumentIngestService> ingestionServiceProvider,
                                        RagProperties ragProperties) {
        this.metadataRepository = metadataRepository;
        this.parsingService = parsingService;
        this.ingestionServiceProvider = ingestionServiceProvider;
        this.ragProperties = ragProperties;
    }

    @Override
    public DocumentMetadataResponse uploadAndProcess(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String filename = (originalFilename != null && !originalFilename.isBlank())
                ? originalFilename
                : "upload";
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        log.info("Uploading document: filename='{}', contentType='{}', size={} bytes",
                filename, contentType, file.getSize());

        DocumentMetadata metadata = DocumentMetadata.newUpload(filename, contentType, file.getSize());
        DocumentMetadata saved = metadataRepository.save(metadata);
        log.debug("Created document metadata: id={}, status={}", saved.getId(), saved.getStatus());

        List<Document> parsedDocuments;
        try {
            parsedDocuments = parsingService.parse(saved.getId(), file);
        } catch (RuntimeException ex) {
            log.error("Failed to parse document {}: {}", saved.getId(), ex.getMessage(), ex);
            saved.fail("failed to parse: " + ex.getMessage());
            metadataRepository.save(saved);
            throw new DocumentProcessingException(
                    saved.getId(),
                    DocumentProcessingException.Stage.EXTRACTION,
                    "could not extract text from document",
                    ex);
        }

        DocumentIngestService ingestionService = ingestionServiceProvider.getIfAvailable();
        if (ingestionService == null) {
            log.warn("No DocumentIngestService available; cannot ingest document {}", saved.getId());
            throw new ProviderNotConfiguredException("pg");
        }

        DocumentMetadata completed = ingestionService.ingest(saved, parsedDocuments);
        return DocumentMetadataResponse.from(completed);
    }
}