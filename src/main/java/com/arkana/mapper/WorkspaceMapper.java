package com.arkana.mapper;

import com.arkana.dto.billing.BillingOverview;
import com.arkana.dto.billing.SubscriptionPlanResponse;
import com.arkana.dto.profile.ProfileResponse;
import com.arkana.dto.reading.ReadingSummaryResponse;
import com.arkana.dto.workspace.DashboardSummaryResponse;
import com.arkana.dto.workspace.WorkspaceBootstrapResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface WorkspaceMapper {
    @Mapping(target = "activeClientCount", source = "activeClientCount")
    @Mapping(target = "inProgressReadingCount", source = "inProgressReadingCount")
    @Mapping(target = "completedReadingCount", source = "completedReadingCount")
    @Mapping(target = "recentReadings", source = "recentReadings")
    DashboardSummaryResponse toDashboard(
        long activeClientCount,
        long inProgressReadingCount,
        long completedReadingCount,
        List<ReadingSummaryResponse> recentReadings);

    @Mapping(target = "profile", source = "profile")
    @Mapping(target = "billing", source = "billing")
    @Mapping(target = "plans", source = "plans")
    @Mapping(target = "dashboard", source = "dashboard")
    WorkspaceBootstrapResponse toResponse(
        ProfileResponse profile,
        BillingOverview billing,
        List<SubscriptionPlanResponse> plans,
        DashboardSummaryResponse dashboard);
}
