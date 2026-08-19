package com.arkana.dto.admin;

import java.time.LocalDate;

public record AnalyticsPeriodResponse(
    LocalDate from,
    LocalDate to,
    LocalDate previousFrom,
    LocalDate previousTo,
    String timeZone,
    String granularity) {
}

