package com.ai.docmind.chat.serviceImpl;

import com.ai.docmind.chat.dtos.ChatRequestDto;
import com.ai.docmind.chat.dtos.ChatResponseDto;
import com.ai.docmind.chat.dtos.CitationDto;
import com.ai.docmind.chat.dtos.SearchRequestDto;
import com.ai.docmind.chat.dtos.SearchResultDto;
import com.ai.docmind.chat.service.RagService;
import com.ai.docmind.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG service implementation using Spring AI's VectorStore and ChatClient.
 */
@Service
@ConditionalOnProperty(prefix = "app.providers.pg", name = "enabled", havingValue = "true")
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final RagProperties ragProperties;

    public RagServiceImpl(ObjectProvider<VectorStore> vectorStoreProvider,
                           ChatClient.Builder chatClientBuilder,
                           RagProperties ragProperties) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.ragProperties = ragProperties;
        
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
        
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Override
    public SearchResultDto searchSimilarChunks(SearchRequestDto request) {
        if (vectorStore == null) {
            log.warn("VectorStore not available; returning empty search result");
            return new SearchResultDto(request.query(), 0, List.of());
        }

        int topK = request.topK() != null ? request.topK() : ragProperties.topK();
        double minSimilarity = request.minSimilarity() != null ? request.minSimilarity() : ragProperties.similarityThreshold();

        log.debug("Searching similar chunks for query='{}', topK={}, minSimilarity={}",
                request.query(), topK, minSimilarity);

        List<Document> matchedDocs = retrieveRelevantDocuments(request, topK, minSimilarity);

        List<CitationDto> citations = matchedDocs.stream()
                .map(this::mapToCitation)
                .collect(Collectors.toList());

        return new SearchResultDto(request.query(), citations.size(), citations);
    }

    @Override
    public ChatResponseDto askQuestion(ChatRequestDto request) {
        long startTime = System.currentTimeMillis();

        if (vectorStore == null) {
            log.warn("VectorStore not available; returning empty answer");
            return new ChatResponseDto(
                    "Vector store not configured. Please enable PostgreSQL provider.",
                    request.conversationId(),
                    List.of(),
                    System.currentTimeMillis() - startTime);
        }

        int topK = request.topK() != null ? request.topK() : ragProperties.topK();
        double minSimilarity = request.minSimilarity() != null ? request.minSimilarity() : ragProperties.similarityThreshold();

        log.debug("Asking question: '{}', documentId={}, topK={}, minSimilarity={}",
                request.question(), request.documentId(), topK, minSimilarity);

        // Retrieve relevant documents
        List<Document> matchedDocs = retrieveRelevantDocuments(
                new SearchRequestDto(request.question(), request.documentId(), topK, minSimilarity),
                topK, minSimilarity);

        // Build citations
        List<CitationDto> citations = matchedDocs.stream()
                .map(this::mapToCitation)
                .collect(Collectors.toList());

        // Build context and prompt
        String contextText = buildContextString(matchedDocs);
        String prompt = buildPrompt(request.question(), contextText);

        // Call chat model
        String answer;
        try {
            answer = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception ex) {
            log.error("Chat model call failed: {}", ex.getMessage(), ex);
            answer = "I encountered an error while generating the answer. Please try again.";
        }

        long responseTimeMs = System.currentTimeMillis() - startTime;
        log.info("Generated answer in {} ms, {} citations", responseTimeMs, citations.size());

        return new ChatResponseDto(answer, request.conversationId(), citations, responseTimeMs);
    }

    @Override
    public Flux<String> streamQuestionAnswer(ChatRequestDto request) {
        if (vectorStore == null) {
            return Flux.just("Vector store not configured. Please enable PostgreSQL provider.");
        }

        int topK = request.topK() != null ? request.topK() : ragProperties.topK();
        double minSimilarity = request.minSimilarity() != null ? request.minSimilarity() : ragProperties.similarityThreshold();

        List<Document> matchedDocs = retrieveRelevantDocuments(
                new SearchRequestDto(request.question(), request.documentId(), topK, minSimilarity),
                topK, minSimilarity);

        String contextText = buildContextString(matchedDocs);
        String prompt = buildPrompt(request.question(), contextText);

        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }

    /**
     * Retrieves relevant documents from the vector store using similarity search.
     */
    private List<Document> retrieveRelevantDocuments(SearchRequestDto request, int topK, double minSimilarity) {
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            Filter.Expression filter = null;

            if (request.documentId() != null) {
                filter = builder.eq("documentId", request.documentId().toString()).build();
            }

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(request.query())
                    .topK(topK)
                    .similarityThreshold(minSimilarity)
                    .filterExpression(filter)
                    .build();

            return vectorStore.similaritySearch(searchRequest);

        } catch (Exception ex) {
            log.error("Similarity search failed: {}", ex.getMessage(), ex);
            return List.of();
        }
    }

    /**
     * Maps a Spring AI Document to a CitationDto.
     */
    private CitationDto mapToCitation(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        UUID documentId = null;
        Object docIdObj = metadata.get("documentId");
        if (docIdObj instanceof String str) {
            documentId = UUID.fromString(str);
        } else if (docIdObj instanceof UUID uuid) {
            documentId = uuid;
        }

        String fileName = getStringMetadata(metadata, "fileName");
        Integer chunkIndex = getIntMetadata(metadata, "chunkIndex");
        Integer pageNumber = getIntMetadata(metadata, "page_number");
        Double score = getDoubleMetadata(metadata, "score");

        // Snippet: first 300 chars of the document text
        String snippet = document.getText();
        if (snippet != null && snippet.length() > 300) {
            snippet = snippet.substring(0, 300) + "...";
        }

        return new CitationDto(
                documentId,
                fileName,
                chunkIndex,
                pageNumber,
                snippet,
                score != null ? 1.0 - score : null, // Convert distance to similarity
                metadata);
    }

    private String getStringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Double getDoubleMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Builds context string from matched documents.
     */
    private String buildContextString(List<Document> documents) {
        if (documents.isEmpty()) {
            return "";
        }

        return documents.stream()
                .map(doc -> {
                    String fileName = getStringMetadata(doc.getMetadata(), "fileName");
                    Integer pageNumber = getIntMetadata(doc.getMetadata(), "page_number");
                    String pageRef = pageNumber != null ? " (page " + pageNumber + ")" : "";
                    return String.format("[Source: %s%s]\n%s", fileName, pageRef, doc.getText());
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * Builds the prompt for the chat model.
     */
    private String buildPrompt(String question, String contextText) {
        if (contextText == null || contextText.isBlank()) {
            return String.format("""
                    You are a helpful assistant. The user asks a question, but no relevant documents were found.
                    
                    Question: %s
                    
                    Answer the question to the best of your knowledge, but mention that no relevant documents were available.
                    """, question);
        }

        return String.format("""
                You are a helpful assistant that answers questions based on the provided context documents.
                
                Context documents:
                %s
                
                Question: %s
                
                Instructions:
                - Answer the question using ONLY the information from the context documents above.
                - If the context does not contain enough information, say so clearly.
                - Cite the source documents by referencing them (e.g., [Source: filename]).
                - Be concise and accurate.
                """, contextText, question);
    }
}