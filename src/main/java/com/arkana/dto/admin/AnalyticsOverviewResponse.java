package com.arkana.dto.admin;

import java.time.OffsetDateTime;
import java.util.List;

public record AnalyticsOverviewResponse(
    AnalyticsPeriodResponse period,
    AnalyticsKpisResponse kpis,
    List<AnalyticsTimelinePointResponse> timeline,
    OffsetDateTime generatedAt) {
}
