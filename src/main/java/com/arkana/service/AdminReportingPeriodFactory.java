package com.arkana.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Component
public class AdminReportingPeriodFactory {
  private static final long MAXIMUM_DAYS = 366;

  public AdminReportingPeriod create(LocalDate from, LocalDate to, String timeZone) {
    if (from == null || to == null || timeZone == null || timeZone.isBlank()) {
      throw invalidPeriod();
    }
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    if (days < 1 || days > MAXIMUM_DAYS) {
      throw invalidPeriod();
    }
    try {
      ZoneId zoneId = ZoneId.of(timeZone);
      LocalDate previousTo = from.minusDays(1);
      LocalDate previousFrom = previousTo.minusDays(days - 1);
      return new AdminReportingPeriod(
          from,
          to,
          previousFrom,
          previousTo,
          zoneId,
          from.atStartOfDay(zoneId).toOffsetDateTime(),
          to.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime(),
          previousFrom.atStartOfDay(zoneId).toOffsetDateTime(),
          previousTo.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime(),
          days > 60 ? "WEEK" : "DAY");
    } catch (DateTimeException exception) {
      throw invalidPeriod();
    }
  }

  private ResponseStatusException invalidPeriod() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The reporting period or timezone is invalid.");
  }
}

