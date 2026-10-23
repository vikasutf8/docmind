package com.ai.docmind.chat.dtos;

import java.util.List;

/** Result of a similarity search. */
public record SearchResultDto(
        String query,
        Integer totalMatches,
        List<CitationDto> matches) {
}