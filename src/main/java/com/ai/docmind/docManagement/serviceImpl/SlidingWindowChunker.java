package com.ai.docmind.docManagement.serviceImpl;

import com.ai.docmind.config.RagProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Character-based sliding window over one page of text. Window and step come
 * from {@code app.rag.chunk-size} / {@code chunk-overlap}; no tokenizer needed.
 */
@Component
public class SlidingWindowChunker {

    private final RagProperties rag;

    public SlidingWindowChunker(RagProperties rag) {
        this.rag = rag;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String content = text.strip();
        int size = rag.chunkSize();
        int step = size - rag.chunkOverlap();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(start + size, content.length());
            String chunk = content.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == content.length()) {
                break;
            }
        }
        return List.copyOf(chunks);
    }
}
