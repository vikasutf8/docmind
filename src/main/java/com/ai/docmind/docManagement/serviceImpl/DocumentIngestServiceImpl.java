package com.ai.docmind.docManagement.serviceImpl;

import com.ai.docmind.constant.AppConstants;
import com.ai.docmind.config.RagProperties;
import com.ai.docmind.docManagement.repository.DocumentMetadataRepository;
import com.ai.docmind.docManagement.service.DocumentIngestService;
import com.ai.docmind.exception.DocumentProcessingException;
import com.ai.docmind.exception.ProviderNotConfiguredException;
import com.ai.docmind.shared.entity.DocumentMetadata;
import com.ai.docmind.shared.enums.DocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits parsed documents into chunks using TokenTextSplitter, enriches metadata,
 * writes embeddings to pgvector, and updates document status.
 */
@Service
@ConditionalOnProperty(prefix = "app.providers.pg", name = "enabled", havingValue = "true")
public class DocumentIngestServiceImpl implements DocumentIngestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestServiceImpl.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final DocumentMetadataRepository metadataRepository;
    private final RagProperties ragProperties;
    private final TokenTextSplitter tokenTextSplitter;

    public DocumentIngestServiceImpl(ObjectProvider<VectorStore> vectorStoreProvider,
                                      DocumentMetadataRepository metadataRepository,
                                      RagProperties ragProperties) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.metadataRepository = metadataRepository;
        this.ragProperties = ragProperties;
        this.tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(1000)
                .withMinChunkSizeChars(400)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(5000)
                .withKeepSeparator(true)
                .build();
    }

    @Override
    public DocumentMetadata ingest(DocumentMetadata metadata, List<Document> documents) {
        UUID documentId = metadata.getId();

        try {
            // Start processing - set status to PROCESSING and totalPages
            metadata.startProcessing();
            metadataRepository.save(metadata);
            log.debug("Document {} moved to PROCESSING, totalPages={}", documentId, documents.size());

            // 1. Text chunking using TokenTextSplitter
            List<Document> chunks;
            try {
                chunks = tokenTextSplitter.apply(documents);
            } catch (RuntimeException ex) {
                log.error("Could not chunk document {}: {}", documentId, ex.getMessage(), ex);
                throw new DocumentProcessingException(
                        documentId,
                        DocumentProcessingException.Stage.CHUNKING,
                        "could not split document into chunks",
                        ex);
            }

            log.debug("Document {} produced {} chunks from {} documents", documentId, chunks.size(), documents.size());

            // 2. Check if chunks array is empty
            if (chunks.isEmpty()) {
                log.warn("Document {} produced no chunks; marking as FAILED", documentId);
                metadata.fail("no chunks produced from document");
                metadataRepository.save(metadata);
                throw new DocumentProcessingException(
                        documentId,
                        DocumentProcessingException.Stage.CHUNKING,
                        "no chunks produced from document");
            }

            // 3. Metadata enrichment on each chunk
            List<Document> enrichedChunks = new ArrayList<>(chunks.size());
            int chunkIndex = 0;
            for (Document chunk : chunks) {
                Map<String, Object> enrichedMetadata = new HashMap<>(chunk.getMetadata());

                // Extract page number from chunk metadata (from PagePdfDocumentReader)
                Object pageNumObj = enrichedMetadata.get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
                int pageNumber = (pageNumObj instanceof Number) ? ((Number) pageNumObj).intValue() : 1;

                enrichedMetadata.put("documentId", documentId.toString());
                enrichedMetadata.put("fileName", metadata.getFilename());
                enrichedMetadata.put("contentType", metadata.getContentType());
                enrichedMetadata.put("chunkIndex", chunkIndex);
                enrichedMetadata.put("page_number", pageNumber);

                Document enrichedChunk = Document.builder()
                        .id(documentId + ":" + chunkIndex)
                        .text(chunk.getText())
                        .metadata(enrichedMetadata)
                        .build();

                enrichedChunks.add(enrichedChunk);
                chunkIndex++;
            }

            // 4. Write chunks and embeddings to pgvector
            VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
            if (vectorStore == null) {
                log.warn("No VectorStore available; cannot embed document {}", documentId);
                throw new ProviderNotConfiguredException(AppConstants.Providers.POSTGRES);
            }

            try {
                vectorStore.add(enrichedChunks);
            } catch (RuntimeException ex) {
                log.error("Could not embed {} chunks for document {}", enrichedChunks.size(), documentId, ex);
                throw new DocumentProcessingException(
                        documentId,
                        DocumentProcessingException.Stage.EMBEDDING,
                        "could not embed document chunks",
                        ex);
            }

            log.info("Embedded {} chunks for document {}", enrichedChunks.size(), documentId);

            // 5. Update document status to INDEXED, set totalChunks, clear errorMessage
            metadata.markIndexed(documents.size(), enrichedChunks.size());
            DocumentMetadata saved = metadataRepository.save(metadata);
            log.info("Document indexed: id={}, filename='{}', totalPages={}, totalChunks={}, status={}",
                    saved.getId(), saved.getFilename(), saved.getTotalPages(), saved.getTotalChunks(), saved.getStatus());

            return saved;

        } catch (RuntimeException ex) {
            // On any failure, mark as FAILED and save
            if (metadata.getStatus() != DocumentStatus.FAILED) {
                String errorMsg = ex.getMessage();
                if (errorMsg == null || errorMsg.isBlank()) {
                    errorMsg = "unknown failure";
                }
                log.error("Document ingestion failed for {}: {}", documentId, errorMsg, ex);
                metadata.fail(errorMsg);
                metadataRepository.save(metadata);
            }
            if (ex instanceof DocumentProcessingException) {
                throw ex;
            }
            throw new DocumentProcessingException(
                    documentId,
                    DocumentProcessingException.Stage.EMBEDDING,
                    "document ingestion failed: " + ex.getMessage(),
                    ex);
        }
    }
}