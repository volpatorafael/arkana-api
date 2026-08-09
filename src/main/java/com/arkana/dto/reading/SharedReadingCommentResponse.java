package com.arkana.dto.reading;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SharedReadingCommentResponse(
    UUID id,
    String body,
    OffsetDateTime createdAt) {
}
