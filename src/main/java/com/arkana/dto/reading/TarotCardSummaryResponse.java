package com.arkana.dto.reading;

public record TarotCardSummaryResponse(
    String id,
    short number,
    String suit,
    String name) {
}
