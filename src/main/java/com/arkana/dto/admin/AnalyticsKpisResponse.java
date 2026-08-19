package com.arkana.dto.admin;

public record AnalyticsKpisResponse(
    MetricComparisonResponse adminAccesses,
    MetricComparisonResponse activatedAccounts,
    MetricComparisonResponse dailyActiveUsers,
    MetricComparisonResponse weeklyActiveUsers,
    MetricComparisonResponse activeTrials,
    MetricComparisonResponse expiredTrials,
    MetricComparisonResponse activeSubscribers,
    SubscriberBreakdownResponse subscriberBreakdown,
    MetricComparisonResponse trialToPaidRate) {
}

