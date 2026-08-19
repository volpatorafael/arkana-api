package com.arkana.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AdminFunnelCalculator {
  public List<AdminAnalyticsResult.FunnelStep> calculate(
      List<AdminAnalyticsResult.FunnelStepKey> keys,
      List<Long> counts) {
    if (keys.size() != counts.size() || keys.isEmpty()) {
      throw new IllegalArgumentException("Funnel keys and counts must have the same non-zero size.");
    }

    long firstCount = counts.getFirst();
    List<AdminAnalyticsResult.FunnelStep> result = new ArrayList<>();
    for (int index = 0; index < keys.size(); index++) {
      long count = counts.get(index);
      long previous = index == 0 ? 0 : counts.get(index - 1);
      result.add(new AdminAnalyticsResult.FunnelStep(
          keys.get(index),
          count,
          ratio(count, firstCount),
          index == 0 || previous == 0 ? null : round(1 - ((double) count / previous), 3)));
    }
    return result;
  }

  private double ratio(long numerator, long denominator) {
    return denominator == 0 ? 0 : round((double) numerator / denominator, 3);
  }

  private double round(double value, int digits) {
    double factor = Math.pow(10, digits);
    return Math.round(value * factor) / factor;
  }
}
