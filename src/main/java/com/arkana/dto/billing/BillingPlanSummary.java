package com.arkana.dto.billing;

import java.util.UUID;

public record BillingPlanSummary(UUID id, String code, String name, String interval, int amount, String currency) {
}
