package com.arkana.dto.reading;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingShareResponse(UUID id, UUID readingId, OffsetDateTime createdAt) {
}
