package com.arkana.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateAdminTarotCardRequest(
    @NotBlank String id,
    @Min(0) int number,
    @NotBlank String suit,
    @NotBlank String imagePath,
    @NotBlank String namePtBr,
    @NotBlank String nameEn,
    @NotBlank String descriptionPtBr,
    @NotBlank String descriptionEn,
    @NotBlank String lightPtBr,
    @NotBlank String lightEn,
    @NotBlank String shadowPtBr,
    @NotBlank String shadowEn) {
}
