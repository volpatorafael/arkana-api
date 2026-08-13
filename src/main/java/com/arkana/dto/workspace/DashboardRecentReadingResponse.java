package com.arkana.dto.workspace;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DashboardRecentReadingResponse(
        UUID id,
        String title,
        String question,
        String spreadName,
        String status,
        OffsetDateTime startedAt) {
}
