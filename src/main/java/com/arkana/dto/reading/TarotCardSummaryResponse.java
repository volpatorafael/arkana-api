package com.arkana.dto.reading;

public record TarotCardSummaryResponse(
    String id,
    String deckId,
    short number,
    String suit,
    String name,
    String imagePath) {
}
