package com.arkana.dto.reading;

import com.arkana.domain.CardOrientation;

import java.math.BigDecimal;

public record SharedReadingPositionResponse(
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
    String interpretation) {
}
