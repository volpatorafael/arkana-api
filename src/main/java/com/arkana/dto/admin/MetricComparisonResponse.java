package com.arkana.dto.admin;

public record MetricComparisonResponse(
    double value,
    double previousValue,
    Double changePercentage,
    String unit) {
}

