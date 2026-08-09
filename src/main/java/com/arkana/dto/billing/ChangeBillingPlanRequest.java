package com.arkana.dto.billing;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeBillingPlanRequest(@NotNull UUID planPriceId) {
}
