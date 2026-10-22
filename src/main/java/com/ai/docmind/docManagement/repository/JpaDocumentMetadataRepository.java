package com.ai.docmind.docManagement.repository;

import com.ai.docmind.shared.entity.DocumentMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Postgres-backed metadata repository, active only when the pg provider is on. */
@Repository
@ConditionalOnProperty(prefix = "app.providers.pg", name = "enabled", havingValue = "true")
public class JpaDocumentMetadataRepository implements DocumentMetadataRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public DocumentMetadata save(DocumentMetadata metadata) {
        return em.merge(metadata);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DocumentMetadata> findById(UUID id) {
        return Optional.ofNullable(em.find(DocumentMetadata.class, id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentMetadata> findAll() {
        return em.createQuery(
                        "select m from DocumentMetadata m order by m.createdAt desc", DocumentMetadata.class)
                .getResultList();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        var entity = em.find(DocumentMetadata.class, id);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
