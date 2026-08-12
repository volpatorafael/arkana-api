package com.arkana.dto.client;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientResponse(
    UUID id,
    String name,
    LocalDate birthDate,
    String email,
    String phone,
    String notes,
    OffsetDateTime archivedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
}
