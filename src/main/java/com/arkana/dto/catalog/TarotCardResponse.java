package com.arkana.dto.catalog;

public record TarotCardResponse(
    String id,
    String deckId,
    int number,
    String suit,
    String name,
    String imagePath,
    String description,
    String lightMeaning,
    String shadowMeaning) {
}
