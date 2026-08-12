package com.arkana.dto.reading;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReadingResponse(
    UUID id,
    UUID clientId,
    UUID readingShareId,
    String spreadId,
    String spreadName,
    String deckMode,
    String status,
    String title,
    String question,
    String context,
    Integer consultationFeeAmount,
    String consultationFeeCurrency,
    Integer consultationDurationMinutes,
    String analysisVideoUrl,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime archivedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    ReadingSpreadSummaryResponse spread,
    List<ReadingPositionResponse> positions) {
}
