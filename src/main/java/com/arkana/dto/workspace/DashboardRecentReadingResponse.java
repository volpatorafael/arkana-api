package com.arkana.dto.workspace;

import com.arkana.domain.ReadingStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardRecentReadingResponse(
        UUID id,
        String title,
        String question,
        String spreadName,
        ReadingStatus status,
        OffsetDateTime startedAt) {
}
