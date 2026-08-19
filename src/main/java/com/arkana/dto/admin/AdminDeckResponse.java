package com.arkana.dto.admin;

public record AdminDeckResponse(
    String id,
    String namePtBr,
    String nameEn,
    int cardCount,
    boolean active,
    int displayOrder) {
}
