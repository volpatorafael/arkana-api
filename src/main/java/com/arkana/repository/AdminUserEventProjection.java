package com.arkana.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserEventProjection(UUID ownerId, OffsetDateTime occurredAt) {
}

