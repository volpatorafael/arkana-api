package com.arkana.dto.reading;

import com.arkana.domain.CardOrientation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReadingPositionResponse(
    UUID id,
    String key,
    short order,
    String name,
    String meaning,
    BigDecimal x,
    BigDecimal y,
    short rotation,
    int stackOrder,
    TarotCardSummaryResponse card,
    CardOrientation orientation,
    String interpretation,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
}
