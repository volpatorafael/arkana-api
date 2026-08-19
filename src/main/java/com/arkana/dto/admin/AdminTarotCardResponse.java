package com.arkana.dto.admin;

public record AdminTarotCardResponse(
    String id,
    String deckId,
    int number,
    String suit,
    String imagePath,
    String namePtBr,
    String nameEn,
    String descriptionPtBr,
    String descriptionEn,
    String lightPtBr,
    String lightEn,
    String shadowPtBr,
    String shadowEn) {
}
