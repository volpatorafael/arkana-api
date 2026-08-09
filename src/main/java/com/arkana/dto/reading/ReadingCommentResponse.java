package com.arkana.dto.reading;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingCommentResponse(
    UUID id,
    UUID readingId,
    String body,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
}
