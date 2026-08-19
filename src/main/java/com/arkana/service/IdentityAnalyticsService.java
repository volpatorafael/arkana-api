package com.arkana.service;

import com.arkana.dto.admin.IdentityAnalyticsOverviewResponse;
import com.arkana.integration.IdentityAnalyticsProvider;
import com.arkana.integration.IdentityAnalyticsProvider.IdentityUser;
import com.arkana.mapper.AdminAnalyticsMapper;
import com.arkana.repository.AdminUserEventProjection;
import com.arkana.repository.AdminUserRepository;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.ClientRepository;
import com.arkana.repository.ProfileRepository;
import com.arkana.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.arkana.service.AdminAnalyticsResult.MetricUnit.COUNT;
import static com.arkana.service.AdminAnalyticsResult.FunnelStepKey.ACCOUNT_CREATED;
import static com.arkana.service.AdminAnalyticsResult.FunnelStepKey.ANOTHER_READING_COMPLETED;
import static com.arkana.service.AdminAnalyticsResult.FunnelStepKey.BECAME_PAYING;
import static com.arkana.service.AdminAnalyticsResult.FunnelStepKey.EMAIL_CONFIRMED;
import static com.arkana.service.AdminAnalyticsResult.FunnelStepKey.FIRST_CLIENT_CREATED;
import static com.arkana.service.AdminAnalyticsResult.FunnelStepKey.FIRST_READING_COMPLETED;
import static com.arkana.service.AdminAnalyticsResult.FunnelStepKey.SESSION_STARTED;
import static com.arkana.service.AdminAnalyticsResult.FunnelStepKey.WORKSPACE_ACTIVATED;

@Service
@RequiredArgsConstructor
public class IdentityAnalyticsService {
  private final AdminReportingPeriodFactory periods;
  private final IdentityAnalyticsProvider identityProvider;
  private final AdminUserRepository adminUsers;
  private final ProfileRepository profiles;
  private final ClientRepository clients;
  private final ReadingRepository readings;
  private final BillingAccountRepository billingAccounts;
  private final AdminFunnelCalculator funnels;
  private final AdminAnalyticsMapper mapper;
  private final Clock clock;

  @Transactional(readOnly = true)
  public IdentityAnalyticsOverviewResponse overview(LocalDate from, LocalDate to, String timeZone) {
    AdminReportingPeriod period = periods.create(from, to, timeZone);
    OffsetDateTime generatedAt = OffsetDateTime.now(clock);
    Set<UUID> adminIds = adminUsers.findAll().stream()
        .map(admin -> admin.getUserId())
        .collect(Collectors.toSet());
    Set<UUID> profileIds = profiles.findAllAdminProfileEvents().stream()
        .map(AdminUserEventProjection::ownerId)
        .collect(Collectors.toSet());
    List<IdentityUser> users = identityProvider.users().stream()
        .filter(user -> !user.anonymous())
        .filter(user -> !adminIds.contains(user.id()))
        .toList();
    List<IdentityUser> currentCohort = cohort(users, period.fromInstant(), period.toExclusiveInstant());
    List<IdentityUser> previousCohort = cohort(
        users, period.previousFromInstant(), period.previousToExclusiveInstant());
    Set<UUID> activatedIds = activatedIds(currentCohort, profileIds);
    IdentityCounts current = counts(currentCohort, activatedIds);
    IdentityCounts previous = counts(previousCohort, activatedIds(previousCohort, profileIds));
    IdentityAnalyticsResult.Metrics metrics = new IdentityAnalyticsResult.Metrics(
        metric(current.createdAccounts(), previous.createdAccounts()),
        metric(current.awaitingConfirmation(), previous.awaitingConfirmation()),
        metric(current.confirmedAccounts(), previous.confirmedAccounts()),
        metric(current.signedInAccounts(), previous.signedInAccounts()),
        metric(current.signedInNotActivated(), previous.signedInNotActivated()),
        metric(current.confirmedNotActivated(), previous.confirmedNotActivated()),
        metric(current.activatedWorkspaces(), previous.activatedWorkspaces()));
    List<AdminAnalyticsResult.FunnelStep> registrationFunnel = funnels.calculate(
        List.of(ACCOUNT_CREATED, EMAIL_CONFIRMED, SESSION_STARTED, WORKSPACE_ACTIVATED),
        List.of(
            current.createdAccounts(),
            current.confirmedAccounts(),
            current.signedInAccounts(),
            current.activatedWorkspaces()));
    List<AdminAnalyticsResult.FunnelStep> productFunnel = productFunnel(
        activatedIds,
        generatedAt);
    return mapper.toResponse(new IdentityAnalyticsResult(
        period.result(),
        metrics,
        registrationFunnel,
        productFunnel,
        generatedAt));
  }

  private List<IdentityUser> cohort(
      List<IdentityUser> users,
      OffsetDateTime from,
      OffsetDateTime to) {
    return users.stream()
        .filter(user -> !user.createdAt().isBefore(from) && user.createdAt().isBefore(to))
        .toList();
  }

  private Set<UUID> activatedIds(List<IdentityUser> cohort, Set<UUID> profileIds) {
    return cohort.stream()
        .filter(user -> user.emailConfirmedAt() != null)
        .filter(user -> user.lastSignInAt() != null)
        .map(IdentityUser::id)
        .filter(profileIds::contains)
        .collect(Collectors.toSet());
  }

  private IdentityCounts counts(List<IdentityUser> cohort, Set<UUID> activatedIds) {
    Predicate<IdentityUser> confirmed = user -> user.emailConfirmedAt() != null;
    Predicate<IdentityUser> signedIn = confirmed.and(user -> user.lastSignInAt() != null);
    Predicate<IdentityUser> activated = user -> activatedIds.contains(user.id());
    long awaiting = cohort.stream().filter(user -> user.emailConfirmedAt() == null).count();
    long confirmedAccounts = cohort.stream().filter(confirmed).count();
    long signedInAccounts = cohort.stream().filter(signedIn).count();
    long signedInNotActivated = cohort.stream()
        .filter(signedIn)
        .filter(activated.negate())
        .count();
    long confirmedNotActivated = cohort.stream()
        .filter(user -> user.emailConfirmedAt() != null)
        .filter(activated.negate())
        .count();
    long activatedWorkspaces = cohort.stream().filter(activated).count();
    return new IdentityCounts(
        cohort.size(),
        awaiting,
        confirmedAccounts,
        signedInAccounts,
        signedInNotActivated,
        confirmedNotActivated,
        activatedWorkspaces);
  }

  private List<AdminAnalyticsResult.FunnelStep> productFunnel(
      Set<UUID> activatedIds,
      OffsetDateTime generatedAt) {
    List<AdminAnalyticsResult.FunnelStepKey> keys = List.of(
        WORKSPACE_ACTIVATED,
        FIRST_CLIENT_CREATED,
        FIRST_READING_COMPLETED,
        ANOTHER_READING_COMPLETED,
        BECAME_PAYING);
    if (activatedIds.isEmpty()) {
      return funnels.calculate(keys, List.of(0L, 0L, 0L, 0L, 0L));
    }

    Set<UUID> withClientIds = Set.copyOf(clients.findDistinctOwnerIdsWithClients(activatedIds));
    Map<UUID, Long> completedByOwner = readings.findCompletedOwnerIds(activatedIds).stream()
        .collect(Collectors.groupingBy(ownerId -> ownerId, Collectors.counting()));
    Set<UUID> firstReadingIds = completedByOwner.keySet().stream()
        .filter(withClientIds::contains)
        .collect(Collectors.toSet());
    Set<UUID> anotherReadingIds = firstReadingIds.stream()
        .filter(ownerId -> completedByOwner.get(ownerId) >= 2)
        .collect(Collectors.toSet());
    long paying = anotherReadingIds.isEmpty()
        ? 0
        : billingAccounts.findActiveSubscriberOwnerIds(anotherReadingIds, generatedAt).size();
    return funnels.calculate(
        keys,
        List.of(
            (long) activatedIds.size(),
            (long) withClientIds.size(),
            (long) firstReadingIds.size(),
            (long) anotherReadingIds.size(),
            paying));
  }

  private AdminAnalyticsResult.Metric metric(long value, long previous) {
    Double change = previous == 0
        ? null
        : Math.round((((double) value - previous) / previous) * 1000) / 10.0;
    return new AdminAnalyticsResult.Metric(value, previous, change, COUNT);
  }

  private record IdentityCounts(
      long createdAccounts,
      long awaitingConfirmation,
      long confirmedAccounts,
      long signedInAccounts,
      long signedInNotActivated,
      long confirmedNotActivated,
      long activatedWorkspaces) {
  }
}
