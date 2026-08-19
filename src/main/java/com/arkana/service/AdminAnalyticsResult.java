package com.arkana.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminAnalyticsResult(
    Period period,
    Kpis kpis,
    List<TimelinePoint> timeline,
    OffsetDateTime generatedAt) {

  public record Period(
      LocalDate from,
      LocalDate to,
      LocalDate previousFrom,
      LocalDate previousTo,
      String timeZone,
      String granularity) {
  }

  public record Metric(double value, double previousValue, Double changePercentage, MetricUnit unit) {
  }

  public enum MetricUnit {
    COUNT,
    PERCENTAGE
  }

  public record Breakdown(long monthly, long annual) {
  }

  public record Kpis(
      Metric adminAccesses,
      Metric activatedAccounts,
      Metric dailyActiveUsers,
      Metric weeklyActiveUsers,
      Metric activeTrials,
      Metric expiredTrials,
      Metric activeSubscribers,
      Breakdown subscriberBreakdown,
      Metric trialToPaidRate) {
  }

  public record TimelinePoint(LocalDate date, long registrations, long completedReadings) {
  }

  public record FunnelStep(FunnelStepKey key, long count, double conversionRate, Double dropOffRate) {
  }

  public enum FunnelStepKey {
    ACCOUNT_CREATED,
    EMAIL_CONFIRMED,
    SESSION_STARTED,
    WORKSPACE_ACTIVATED,
    FIRST_CLIENT_CREATED,
    FIRST_READING_COMPLETED,
    ANOTHER_READING_COMPLETED,
    BECAME_PAYING
  }
}
