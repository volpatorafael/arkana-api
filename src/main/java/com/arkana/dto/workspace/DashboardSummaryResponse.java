package com.arkana.dto.workspace;

import com.arkana.dto.reading.ReadingSummaryResponse;

import java.util.List;

public record DashboardSummaryResponse(
        long activeClientCount,
        long inProgressReadingCount,
        long completedReadingCount,
        List<ReadingSummaryResponse> recentReadings) {
}
