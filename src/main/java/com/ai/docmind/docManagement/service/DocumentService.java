package com.ai.docmind.docManagement.service;

import com.ai.docmind.docManagement.dtos.CreateDocumentRequest;
import com.ai.docmind.docManagement.dtos.DocumentResponse;
import com.ai.docmind.docManagement.dtos.UpdateDocumentRequest;
import java.util.List;
import java.util.UUID;

/** Document use-cases. Returns DTOs only — never the shared entity. */
public interface DocumentService {

    DocumentResponse create(CreateDocumentRequest request);

    DocumentResponse getById(UUID id);

    List<DocumentResponse> list();

    DocumentResponse update(UUID id, UpdateDocumentRequest request);

    void delete(UUID id);
}
