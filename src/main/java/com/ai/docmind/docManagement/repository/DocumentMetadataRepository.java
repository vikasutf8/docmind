package com.ai.docmind.docManagement.repository;

import com.ai.docmind.shared.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence contract for uploaded-file metadata. */
public interface DocumentMetadataRepository   {
//extends JpaRepository<DocumentMetadata, UUID>
    DocumentMetadata save(DocumentMetadata metadata);

    Optional<DocumentMetadata> findById(UUID id);

    List<DocumentMetadata> findAll();

    void deleteById(UUID id);
}
