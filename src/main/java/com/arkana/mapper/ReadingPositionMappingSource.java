package com.arkana.mapper;

import com.arkana.domain.ReadingPosition;
import com.arkana.domain.TarotCard;

public record ReadingPositionMappingSource(
    ReadingPosition position,
    TarotCard card) {
}
