package com.ai.docmind.docManagement.serviceImpl;

import com.ai.docmind.docManagement.service.DocumentParsingService;
import com.ai.docmind.exception.DocumentProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Parses uploaded files into Spring AI Documents.
 * Uses PagePdfDocumentReader for PDFs and TikaDocumentReader for other formats.
 */
@Service
public class DocumentParsingServiceImpl implements DocumentParsingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParsingServiceImpl.class);

    @Override
    public List<Document> parse(UUID documentId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String filename = (originalFilename != null && !originalFilename.isBlank())
                ? originalFilename
                : "upload";
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        log.debug("Parsing document {}: filename='{}', contentType='{}'", documentId, filename, contentType);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            log.error("Failed to read file bytes for document {}: {}", documentId, ex.getMessage(), ex);
            throw new DocumentProcessingException(
                    documentId,
                    DocumentProcessingException.Stage.EXTRACTION,
                    "could not read file bytes",
                    ex);
        }

        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        boolean isPdf = isPdf(filename, contentType);
        try {
            if (isPdf) {
                return parseGenericPdf(resource);
            } else {
                return parseGenericFile(resource);
            }
        } catch (RuntimeException ex) {
            log.error("Failed to parse document {}: {}", documentId, ex.getMessage(), ex);
            throw new DocumentProcessingException(
                    documentId,
                    DocumentProcessingException.Stage.EXTRACTION,
                    "could not extract text from document",
                    ex);
        }
    }

    private boolean isPdf(String filename, String contentType) {
        boolean pdfByName = filename != null && filename.toLowerCase().endsWith(".pdf");
        boolean pdfByType = contentType != null && contentType.toLowerCase().contains("pdf");
        return pdfByName && pdfByType;
    }

    private List<Document> parseGenericPdf(ByteArrayResource resource) {
        log.debug("Parsing PDF with PagePdfDocumentReader");
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource, PdfDocumentReaderConfig.defaultConfig());
        return reader.get();
    }

    private List<Document> parseGenericFile(ByteArrayResource resource) {
        log.debug("Parsing generic file with TikaDocumentReader");
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        return reader.read();
    }
}