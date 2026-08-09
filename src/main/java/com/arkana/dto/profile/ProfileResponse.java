package com.arkana.dto.profile;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProfileResponse(
    UUID id,
    String email,
    String displayName,
    String locale,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
}
