package com.arkana.mapper;

import com.arkana.dto.billing.BillingOverview;
import com.arkana.dto.billing.SubscriptionPlanResponse;
import com.arkana.dto.profile.ProfileResponse;
import com.arkana.dto.workspace.DashboardRecentReadingResponse;
import com.arkana.dto.workspace.DashboardSummaryResponse;
import com.arkana.dto.workspace.WorkspaceBootstrapResponse;
import com.arkana.repository.DashboardCountsProjection;
import com.arkana.repository.DashboardRecentReadingProjection;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface WorkspaceMapper {
    @Mapping(target = "activeClientCount", source = "counts.activeClientCount")
    @Mapping(target = "inProgressReadingCount", source = "counts.inProgressReadingCount")
    @Mapping(target = "completedReadingCount", source = "counts.completedReadingCount")
    @Mapping(target = "recentReadings", source = "recentReadings")
    DashboardSummaryResponse toDashboard(
        DashboardCountsProjection counts,
        List<DashboardRecentReadingResponse> recentReadings);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "question", source = "question")
    @Mapping(target = "spreadName", source = "spreadName")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "startedAt", source = "startedAt")
    DashboardRecentReadingResponse toRecentReading(DashboardRecentReadingProjection reading);

    List<DashboardRecentReadingResponse> toRecentReadings(
        List<DashboardRecentReadingProjection> readings);

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
