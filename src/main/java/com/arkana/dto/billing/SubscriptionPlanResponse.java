package com.arkana.dto.billing;

import java.util.List;
import java.util.UUID;

public record SubscriptionPlanResponse(UUID id, String code, String name, String interval, int amount,
                                       Integer compareAtAmount, String currency, int trialDays,
                                       Double annualSavingsPercent,
                                       List<String> availablePaymentMethods, Object promotion) {
}
