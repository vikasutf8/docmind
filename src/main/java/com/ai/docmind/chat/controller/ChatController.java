package com.ai.docmind.chat.controller;

import com.ai.docmind.chat.dtos.ChatRequestDto;
import com.ai.docmind.chat.dtos.ChatResponseDto;
import com.ai.docmind.chat.dtos.SearchRequestDto;
import com.ai.docmind.chat.dtos.SearchResultDto;
import com.ai.docmind.chat.service.RagService;
import com.ai.docmind.constant.AppConstants;
import com.ai.docmind.exception.ProviderNotConfiguredException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * RAG chat endpoints for question answering with document citations.
 */
@RestController
@RequestMapping(AppConstants.Api.V1 + "/chat")
@Tag(name = "chat", description = "RAG chat endpoints for document Q&A")
public class ChatController {

    private final ObjectProvider<RagService> ragServiceProvider;

    public ChatController(ObjectProvider<RagService> ragServiceProvider) {
        this.ragServiceProvider = ragServiceProvider;
    }

    @PostMapping("/search")
    @Operation(summary = "Search for similar document chunks",
            description = "Performs a similarity search against the vector store and returns matching chunks with citations.")
    public ResponseEntity<SearchResultDto> searchSimilarChunks(@RequestBody SearchRequestDto request) {
        RagService service = requireService();
        SearchResultDto result = service.searchSimilarChunks(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question using RAG",
            description = "Retrieves relevant document chunks, builds context, and generates an answer using the chat model. Returns answer with citations.")
    public ResponseEntity<ChatResponseDto> askQuestion(@RequestBody ChatRequestDto request) {
        RagService service = requireService();
        ChatResponseDto response = service.askQuestion(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream a question answer using RAG (Server-Sent Events)",
            description = "Streams the answer token by token as Server-Sent Events. Retrieves relevant documents and generates a streaming response.")
    public Flux<String> streamQuestionAnswer(
            @RequestParam String question,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) Double minSimilarity,
            @RequestParam(required = false) String conversationId) {
        RagService service = requireService();
        ChatRequestDto request = new ChatRequestDto(
                question,
                documentId != null ? UUID.fromString(documentId) : null,
                topK,
                minSimilarity,
                conversationId);
        return service.streamQuestionAnswer(request);
    }

    private RagService requireService() {
        RagService available = ragServiceProvider.getIfAvailable();
        if (available == null) {
            throw new ProviderNotConfiguredException("pg");
        }
        return available;
    }
}
