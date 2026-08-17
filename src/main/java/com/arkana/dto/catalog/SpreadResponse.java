package com.arkana.dto.catalog;

import com.arkana.domain.SpreadKind;

import java.util.List;

public record SpreadResponse(
    String id,
    String name,
    String shortDescription,
    String description,
    String useCase,
    SpreadKind kind,
    int positionCount,
    boolean active,
    List<SpreadPositionResponse> positions) {
}
