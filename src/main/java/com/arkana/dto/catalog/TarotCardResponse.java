package com.arkana.dto.catalog;

public record TarotCardResponse(
    String id,
    int number,
    String suit,
    String name,
    String description,
    String lightMeaning,
    String shadowMeaning) {
}
