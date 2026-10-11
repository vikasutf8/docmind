package com.ai.docmind.chat.controller;

import com.ai.docmind.constant.AppConstants;
import com.ai.docmind.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dummy chat endpoint. Echoes the message back — no AI call yet. Real RAG
 * service wiring (ChatService, DTOs, VectorStore) lands in Phase 1 without
 * changing this path.
 */
@RestController
@RequestMapping(AppConstants.Api.V1 + "/chat")
@Tag(name = "chat", description = "Dummy chat endpoint (RAG wiring later)")
public class ChatController {

    @PostMapping
    @Operation(summary = "Dummy chat — echoes the message")
    public Map<String, String> chat(@RequestBody Map<String, String> body) {
        String message = body == null ? null : body.get("message");
        if (message == null || message.isBlank()) {
            throw new BadRequestException("message must not be blank");
        }
        return Map.of("reply", "echo: " + message);
    }
}
