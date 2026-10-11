package com.ai.docmind.docManagement.serviceImpl;

import com.ai.docmind.docManagement.dtos.CreateDocumentRequest;
import com.ai.docmind.docManagement.dtos.DocumentResponse;
import com.ai.docmind.docManagement.dtos.UpdateDocumentRequest;
import com.ai.docmind.docManagement.repository.DocumentRepository;
import com.ai.docmind.docManagement.service.DocumentService;
import com.ai.docmind.exception.ResourceNotFoundException;
import com.ai.docmind.shared.entity.Document;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Default service implementation backed by {@link DocumentRepository}. */
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository repository;

    public DocumentServiceImpl(DocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public DocumentResponse create(CreateDocumentRequest request) {
        Document saved = repository.save(
                Document.newDocument(request.title(), request.content(), request.contentType()));
        return DocumentResponse.from(saved);
    }

    @Override
    public DocumentResponse getById(UUID id) {
        return repository
                .findById(id)
                .map(DocumentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id.toString()));
    }

    @Override
    public List<DocumentResponse> list() {
        return repository.findAll().stream().map(DocumentResponse::from).toList();
    }

    @Override
    public DocumentResponse update(UUID id, UpdateDocumentRequest request) {
        Document existing = repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id.toString()));
        Document saved = repository.save(existing.withContent(request.title(), request.content()));
        return DocumentResponse.from(saved);
    }

    @Override
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Document", id.toString());
        }
        repository.deleteById(id);
    }
}
