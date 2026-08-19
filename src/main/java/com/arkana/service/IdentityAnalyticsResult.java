package com.arkana.service;

import java.time.OffsetDateTime;
import java.util.List;

public record IdentityAnalyticsResult(
    AdminAnalyticsResult.Period period,
    Metrics metrics,
    List<AdminAnalyticsResult.FunnelStep> registrationFunnel,
    List<AdminAnalyticsResult.FunnelStep> productFunnel,
    OffsetDateTime generatedAt) {

  public record Metrics(
      AdminAnalyticsResult.Metric createdAccounts,
      AdminAnalyticsResult.Metric awaitingConfirmation,
      AdminAnalyticsResult.Metric confirmedAccounts,
      AdminAnalyticsResult.Metric signedInAccounts,
      AdminAnalyticsResult.Metric signedInNotActivated,
      AdminAnalyticsResult.Metric confirmedNotActivated,
      AdminAnalyticsResult.Metric activatedWorkspaces) {
  }
}
