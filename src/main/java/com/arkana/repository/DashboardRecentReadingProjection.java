package com.arkana.repository;

import com.arkana.domain.ReadingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardRecentReadingProjection(
        UUID id,
        String title,
        String question,
        String spreadName,
        ReadingStatus status,
        OffsetDateTime startedAt) {
}
