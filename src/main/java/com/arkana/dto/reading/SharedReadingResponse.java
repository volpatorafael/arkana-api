package com.arkana.dto.reading;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SharedReadingResponse(
    UUID id,
    String title,
    String question,
    ReadingSpreadSummaryResponse spread,
    String deckMode,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    String readerDisplayName,
    List<SharedReadingPositionResponse> positions,
    List<SharedReadingCommentResponse> comments) {
}
