package com.arkana.dto.admin;

public record IdentityAnalyticsMetricsResponse(
    MetricComparisonResponse createdAccounts,
    MetricComparisonResponse awaitingConfirmation,
    MetricComparisonResponse confirmedAccounts,
    MetricComparisonResponse signedInAccounts,
    MetricComparisonResponse signedInNotActivated,
    MetricComparisonResponse confirmedNotActivated,
    MetricComparisonResponse activatedWorkspaces) {
}
