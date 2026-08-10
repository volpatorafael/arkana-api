package com.arkana.dto.billing;

import java.time.OffsetDateTime;
import java.util.List;

public record BillingOverview(String status, String accessStatus, OffsetDateTime trialStartedAt,
                              OffsetDateTime trialEndsAt, OffsetDateTime currentPeriodStart,
                              OffsetDateTime currentPeriodEnd,
                              boolean cancelAtPeriodEnd, BillingPlanSummary currentPlan,
                              BillingPlanSummary scheduledPlan, BillingPlanSummary pendingPlan,
                              OffsetDateTime nextChargeAt,
                              OffsetDateTime overrideEndsAt, List<String> availablePaymentMethods, boolean canCheckout,
                              boolean canCancel, boolean canChangePlan, BillingPromotionResponse promotion) {
}
