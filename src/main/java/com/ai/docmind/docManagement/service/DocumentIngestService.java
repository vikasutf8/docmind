package com.ai.docmind.docManagement.service;

import com.ai.docmind.shared.entity.DocumentMetadata;
import java.util.List;
import org.springframework.ai.document.Document;

/** Chunks parsed documents, enriches metadata, writes to vector store, updates status. */
public interface DocumentIngestService {

    DocumentMetadata ingest(DocumentMetadata metadata, List<Document> documents);
}
