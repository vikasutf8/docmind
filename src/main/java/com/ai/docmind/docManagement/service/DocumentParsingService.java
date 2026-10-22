package com.ai.docmind.docManagement.service;

import java.util.List;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

/** Extracts text documents from an uploaded file. */
public interface DocumentParsingService {

    List<Document> parse(UUID documentId, MultipartFile file);
}
