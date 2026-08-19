package com.arkana.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record UpdateAdminTarotCardRequest(
    @NotBlank String namePtBr,
    @NotBlank String nameEn,
    @NotBlank String descriptionPtBr,
    @NotBlank String descriptionEn,
    @NotBlank String lightPtBr,
    @NotBlank String lightEn,
    @NotBlank String shadowPtBr,
    @NotBlank String shadowEn) {
}
