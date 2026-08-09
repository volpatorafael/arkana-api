package com.arkana.observability;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ArkanaMetric {
  REPOSITORY_QUERY_COUNT("Total repository query invocations"),
  REPOSITORY_QUERY_DURATION("Repository query duration");

  private final String description;

  public String metricName() {
    return "arkana_" + name().toLowerCase(Locale.ROOT);
  }
}
