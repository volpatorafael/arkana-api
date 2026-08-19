package com.arkana.dto.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse(
    UUID id,
    String email,
    String displayName,
    OffsetDateTime createdAt,
    OffsetDateTime lastSignInAt,
    boolean hasProfile,
    int clientCount,
    int readingCount
) {}
