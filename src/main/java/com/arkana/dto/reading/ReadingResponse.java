package com.arkana.dto.reading;

import com.arkana.domain.CurrencyCode;
import com.arkana.domain.ReadingDeckMode;
import com.arkana.domain.ReadingStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReadingResponse(
    UUID id,
    UUID clientId,
    UUID readingShareId,
    String spreadId,
    String spreadName,
    String deckId,
    ReadingDeckMode deckMode,
    ReadingStatus status,
    String title,
    String question,
    String context,
    Integer consultationFeeAmount,
    CurrencyCode consultationFeeCurrency,
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
