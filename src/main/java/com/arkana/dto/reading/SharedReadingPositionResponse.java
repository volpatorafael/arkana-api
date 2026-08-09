package com.arkana.dto.reading;

import java.math.BigDecimal;

public record SharedReadingPositionResponse(
    String key,
    short order,
    String name,
    String meaning,
    BigDecimal x,
    BigDecimal y,
    short rotation,
    SharedTarotCardResponse card,
    String orientation,
    String interpretation) {
}
