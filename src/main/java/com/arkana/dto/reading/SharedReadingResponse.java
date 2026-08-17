package com.arkana.dto.reading;

import com.arkana.domain.ReadingDeckMode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SharedReadingResponse(
    UUID id,
    String title,
    String question,
    ReadingSpreadSummaryResponse spread,
    ReadingDeckMode deckMode,
    String analysisVideoUrl,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    String readerDisplayName,
    List<SharedReadingPositionResponse> positions,
    List<SharedReadingCommentResponse> comments) {
}
