package com.arkana.mapper;

import com.arkana.dto.admin.ActivationFunnelStepResponse;
import com.arkana.dto.admin.AnalyticsKpisResponse;
import com.arkana.dto.admin.AnalyticsOverviewResponse;
import com.arkana.dto.admin.AnalyticsPeriodResponse;
import com.arkana.dto.admin.AnalyticsTimelinePointResponse;
import com.arkana.dto.admin.IdentityAnalyticsMetricsResponse;
import com.arkana.dto.admin.IdentityAnalyticsOverviewResponse;
import com.arkana.dto.admin.MetricComparisonResponse;
import com.arkana.dto.admin.SubscriberBreakdownResponse;
import com.arkana.service.AdminAnalyticsResult;
import com.arkana.service.IdentityAnalyticsResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AdminAnalyticsMapper {

  AnalyticsOverviewResponse toResponse(AdminAnalyticsResult source);

  IdentityAnalyticsOverviewResponse toResponse(IdentityAnalyticsResult source);

  AnalyticsPeriodResponse toResponse(AdminAnalyticsResult.Period source);

  MetricComparisonResponse toResponse(AdminAnalyticsResult.Metric source);

  SubscriberBreakdownResponse toResponse(AdminAnalyticsResult.Breakdown source);

  AnalyticsKpisResponse toResponse(AdminAnalyticsResult.Kpis source);

  AnalyticsTimelinePointResponse toResponse(AdminAnalyticsResult.TimelinePoint source);

  ActivationFunnelStepResponse toResponse(AdminAnalyticsResult.FunnelStep source);

  IdentityAnalyticsMetricsResponse toResponse(IdentityAnalyticsResult.Metrics source);
}

