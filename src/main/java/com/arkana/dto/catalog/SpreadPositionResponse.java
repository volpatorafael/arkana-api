package com.arkana.dto.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record SpreadPositionResponse(
    UUID id,
    String key,
    int order,
    String name,
    String meaning,
    BigDecimal x,
    BigDecimal y,
    int rotation) {
}
