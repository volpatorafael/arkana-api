package com.arkana.dto.admin;

import java.time.OffsetDateTime;
import java.util.List;

public record IdentityAnalyticsOverviewResponse(
    AnalyticsPeriodResponse period,
    IdentityAnalyticsMetricsResponse metrics,
    List<ActivationFunnelStepResponse> registrationFunnel,
    List<ActivationFunnelStepResponse> productFunnel,
    OffsetDateTime generatedAt) {
}
