package com.arkana.dto.workspace;

import java.util.List;

public record DashboardSummaryResponse(
        long activeClientCount,
        long inProgressReadingCount,
        long completedReadingCount,
        List<DashboardRecentReadingResponse> recentReadings) {
}
