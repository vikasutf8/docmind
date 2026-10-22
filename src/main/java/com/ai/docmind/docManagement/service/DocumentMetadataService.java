package com.ai.docmind.docManagement.service;

import com.ai.docmind.docManagement.dtos.DocumentMetadataResponse;
import org.springframework.web.multipart.MultipartFile;

/** Upload and process operations for source documents. */
public interface DocumentMetadataService {

    DocumentMetadataResponse uploadAndProcess(MultipartFile file);

}