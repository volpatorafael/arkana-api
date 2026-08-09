package com.arkana.dto.catalog;

import java.util.List;

public record SpreadResponse(
    String id,
    String name,
    String shortDescription,
    String description,
    String useCase,
    int positionCount,
    boolean active,
    List<SpreadPositionResponse> positions) {
}
