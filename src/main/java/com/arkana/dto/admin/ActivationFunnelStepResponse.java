package com.arkana.dto.admin;

public record ActivationFunnelStepResponse(
    String key,
    long count,
    double conversionRate,
    Double dropOffRate) {
}

