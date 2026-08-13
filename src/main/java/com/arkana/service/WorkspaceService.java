package com.arkana.service;

import com.arkana.domain.ReadingStatus;
import com.arkana.dto.billing.BillingOverview;
import com.arkana.dto.billing.SubscriptionPlanResponse;
import com.arkana.dto.profile.ProfileResponse;
import com.arkana.dto.reading.ReadingSummaryResponse;
import com.arkana.dto.workspace.DashboardSummaryResponse;
import com.arkana.dto.workspace.WorkspaceBootstrapResponse;
import com.arkana.mapper.WorkspaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private static final String ACTIVE_ACCESS = "ACTIVE";

    private final ProfileService profiles;
    private final BillingService billing;
    private final ClientService clients;
    private final ReadingService readings;
    private final WorkspaceMapper mapper;

    public WorkspaceBootstrapResponse bootstrap(
        UUID userId,
        String email,
        String locale) {
        validate(locale);
        ProfileResponse profile = profiles.get(userId, email);
        BillingOverview billingOverview = billing.startTrial(userId);
        List<SubscriptionPlanResponse> plans = null;
        DashboardSummaryResponse dashboard = null;

        if (ACTIVE_ACCESS.equals(billingOverview.accessStatus())) {
            long activeClientCount = clients.countActiveForAuthorizedUser(userId);
            long inProgressReadingCount = readings.countActiveForAuthorizedUser(
                userId,
                ReadingStatus.IN_PROGRESS);
            long completedReadingCount = readings.countActiveForAuthorizedUser(
                userId,
                ReadingStatus.COMPLETED);
            List<ReadingSummaryResponse> recentReadings = readings.recentForAuthorizedUser(
                userId,
                5,
                locale);
            dashboard = mapper.toDashboard(
                activeClientCount,
                inProgressReadingCount,
                completedReadingCount,
                recentReadings);
        } else {
            plans = billing.eligiblePlans(userId);
        }

        return mapper.toResponse(profile, billingOverview, plans, dashboard);
    }

    private void validate(String locale) {
        if (locale != null && !locale.equals("pt-BR") && !locale.equals("en")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "locale must be pt-BR or en.");
        }
    }
}
