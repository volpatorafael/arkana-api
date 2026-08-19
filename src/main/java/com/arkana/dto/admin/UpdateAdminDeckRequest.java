package com.arkana.dto.admin;

public record UpdateAdminDeckRequest(
    String namePtBr,
    String nameEn,
    Integer displayOrder,
    Boolean active) {
}
