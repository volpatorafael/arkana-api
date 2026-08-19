package com.arkana.dto.admin;

import java.time.LocalDate;

public record AnalyticsTimelinePointResponse(
    LocalDate date,
    long registrations,
    long completedReadings) {
}

