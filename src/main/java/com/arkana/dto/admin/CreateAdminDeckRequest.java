package com.arkana.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record CreateAdminDeckRequest(
    @NotBlank String id,
    @NotBlank String namePtBr,
    @NotBlank String nameEn,
    Integer displayOrder,
    Boolean active) {
}
