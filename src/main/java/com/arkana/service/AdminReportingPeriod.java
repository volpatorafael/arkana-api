package com.arkana.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record AdminReportingPeriod(
    LocalDate from,
    LocalDate to,
    LocalDate previousFrom,
    LocalDate previousTo,
    ZoneId zoneId,
    OffsetDateTime fromInstant,
    OffsetDateTime toExclusiveInstant,
    OffsetDateTime previousFromInstant,
    OffsetDateTime previousToExclusiveInstant,
    String granularity) {

  AdminAnalyticsResult.Period result() {
    return new AdminAnalyticsResult.Period(
        from,
        to,
        previousFrom,
        previousTo,
        zoneId.getId(),
        granularity);
  }
}

