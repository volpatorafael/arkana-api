package com.arkana.dto.billing;

import java.time.OffsetDateTime;
import java.util.List;

public record BillingOverview(String status, String accessStatus, OffsetDateTime trialStartedAt,
                              OffsetDateTime trialEndsAt, OffsetDateTime currentPeriodStart,
                              OffsetDateTime currentPeriodEnd,
                              boolean cancelAtPeriodEnd, BillingPlanSummary currentPlan, BillingPlanSummary pendingPlan,
                              OffsetDateTime overrideEndsAt, List<String> availablePaymentMethods, boolean canCheckout,
                              boolean canCancel, boolean canChangePlan, Object promotion) {
}
