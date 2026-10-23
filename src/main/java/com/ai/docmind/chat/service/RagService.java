package com.ai.docmind.chat.service;

import com.ai.docmind.chat.dtos.ChatRequestDto;
import com.ai.docmind.chat.dtos.ChatResponseDto;
import com.ai.docmind.chat.dtos.SearchRequestDto;
import com.ai.docmind.chat.dtos.SearchResultDto;
import reactor.core.publisher.Flux;

/** RAG service for search and question answering. */
public interface RagService {

    SearchResultDto searchSimilarChunks(SearchRequestDto request);

    ChatResponseDto askQuestion(ChatRequestDto request);

    Flux<String> streamQuestionAnswer(ChatRequestDto request);
}