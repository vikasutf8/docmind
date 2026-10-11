package com.ai.docmind.docManagement.repository;

import com.ai.docmind.shared.entity.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain repository contract — intentionally no Spring Data imports so the
 * in-memory impl can later be swapped for JPA/Mongo without touching
 * service or controller signatures.
 */
public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(UUID id);

    List<Document> findAll();

    void deleteById(UUID id);

    boolean existsById(UUID id);
}
