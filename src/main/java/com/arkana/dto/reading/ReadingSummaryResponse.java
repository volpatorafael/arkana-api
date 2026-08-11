package com.arkana.dto.reading;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingSummaryResponse(
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
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime archivedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
}
