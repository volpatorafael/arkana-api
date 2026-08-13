package com.arkana.dto.workspace;

import com.arkana.dto.billing.BillingOverview;
import com.arkana.dto.billing.SubscriptionPlanResponse;
import com.arkana.dto.profile.ProfileResponse;

import java.util.List;

public record WorkspaceBootstrapResponse(
        ProfileResponse profile,
        BillingOverview billing,
        List<SubscriptionPlanResponse> plans,
        DashboardSummaryResponse dashboard) {
}
