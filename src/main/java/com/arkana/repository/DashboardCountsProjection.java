package com.arkana.repository;

public record DashboardCountsProjection(
        long activeClientCount,
        long inProgressReadingCount,
        long completedReadingCount) {
}
