package com.ai.docmind.docManagement.repository;

import com.ai.docmind.shared.entity.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory store — keeps tests green with zero DBs. Replace with JPA later. */
@Repository
public class InMemoryDocumentRepository implements DocumentRepository {

    private final ConcurrentHashMap<UUID, Document> store = new ConcurrentHashMap<>();

    @Override
    public Document save(Document document) {
        store.put(document.id(), document);
        return document;
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Document> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public void deleteById(UUID id) {
        store.remove(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return store.containsKey(id);
    }
}
