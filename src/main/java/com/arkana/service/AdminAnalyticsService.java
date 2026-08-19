package com.arkana.service;

import com.arkana.domain.AdminUser;
import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingPlanPrice;
import com.arkana.dto.admin.AnalyticsOverviewResponse;
import com.arkana.mapper.AdminAnalyticsMapper;
import com.arkana.repository.AdminUserEventProjection;
import com.arkana.repository.AdminUserRepository;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingPlanPriceRepository;
import com.arkana.repository.ClientRepository;
import com.arkana.repository.ProfileRepository;
import com.arkana.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.arkana.service.AdminAnalyticsResult.MetricUnit.COUNT;
import static com.arkana.service.AdminAnalyticsResult.MetricUnit.PERCENTAGE;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {
  private final AdminReportingPeriodFactory periods;
  private final AdminUserRepository adminUsers;
  private final ProfileRepository profiles;
  private final ClientRepository clients;
  private final ReadingRepository readings;
  private final BillingAccountRepository billingAccounts;
  private final BillingPlanPriceRepository billingPlans;
  private final AdminAnalyticsMapper mapper;
  private final Clock clock;

  @Transactional(readOnly = true)
  public AnalyticsOverviewResponse overview(LocalDate from, LocalDate to, String timeZone) {
    AdminReportingPeriod period = periods.create(from, to, timeZone);
    OffsetDateTime generatedAt = OffsetDateTime.now(clock);
    List<AdminUser> administrators = adminUsers.findAll();
    Set<UUID> adminIds = administrators.stream().map(AdminUser::getUserId).collect(Collectors.toSet());
    List<AdminUserEventProjection> profileEvents = profiles.findAllAdminProfileEvents().stream()
        .filter(event -> !adminIds.contains(event.ownerId()))
        .toList();

    long adminUsersCount = adminUsers.countAll();
    AdminAnalyticsResult.Metric adminAccesses = metric(
        adminUsersCount,
        administrators.stream()
            .filter(admin -> admin.getCreatedAt().isBefore(period.fromInstant()))
            .count(),
        COUNT);
    AdminAnalyticsResult.Metric activatedAccounts = metric(
        profileEvents.size(),
        countBefore(profileEvents, period.fromInstant()),
        COUNT);

    LocalDate today = ZonedDateTime.now(clock).withZoneSameInstant(period.zoneId()).toLocalDate();
    OffsetDateTime todayStart = today.atStartOfDay(period.zoneId()).toOffsetDateTime();
    OffsetDateTime tomorrowStart = today.plusDays(1).atStartOfDay(period.zoneId()).toOffsetDateTime();
    OffsetDateTime wauStart = today.minusDays(6).atStartOfDay(period.zoneId()).toOffsetDateTime();
    OffsetDateTime previousWauStart = today.minusDays(13).atStartOfDay(period.zoneId()).toOffsetDateTime();
    List<AdminUserEventProjection> activityEvents = activityEvents(previousWauStart, tomorrowStart, adminIds);
    AdminAnalyticsResult.Metric dailyActiveUsers = metric(
        distinctOwners(activityEvents, todayStart, tomorrowStart),
        distinctOwners(activityEvents, todayStart.minusDays(1), todayStart),
        COUNT);
    AdminAnalyticsResult.Metric weeklyActiveUsers = metric(
        distinctOwners(activityEvents, wauStart, tomorrowStart),
        distinctOwners(activityEvents, previousWauStart, wauStart),
        COUNT);

    List<BillingAccount> accounts = billingAccounts.findAll().stream()
        .filter(account -> !adminIds.contains(account.getOwnerId()))
        .toList();
    Map<UUID, BillingPlanPrice> plansById = billingPlans.findAll().stream()
        .collect(Collectors.toMap(BillingPlanPrice::getId, plan -> plan));
    BillingSnapshot currentBilling = billingSnapshot(accounts, plansById, generatedAt);
    BillingSnapshot previousBilling = billingSnapshot(accounts, plansById, period.fromInstant());

    AdminAnalyticsResult.Kpis kpis = new AdminAnalyticsResult.Kpis(
        adminAccesses,
        activatedAccounts,
        dailyActiveUsers,
        weeklyActiveUsers,
        metric(currentBilling.activeTrials(), previousBilling.activeTrials(), COUNT),
        metric(currentBilling.expiredTrials(), previousBilling.expiredTrials(), COUNT),
        metric(currentBilling.activeSubscribers(), previousBilling.activeSubscribers(), COUNT),
        new AdminAnalyticsResult.Breakdown(currentBilling.monthlySubscribers(), currentBilling.annualSubscribers()),
        metric(currentBilling.trialToPaidRate(), previousBilling.trialToPaidRate(), PERCENTAGE));

    List<AdminUserEventProjection> completedEvents = readings.findAdminCompletedEvents(
            period.previousFromInstant(),
            period.toExclusiveInstant()).stream()
        .filter(event -> !adminIds.contains(event.ownerId()))
        .toList();
    List<AdminAnalyticsResult.TimelinePoint> timeline = timeline(period, profileEvents, completedEvents);
    return mapper.toResponse(new AdminAnalyticsResult(period.result(), kpis, timeline, generatedAt));
  }

  private List<AdminUserEventProjection> activityEvents(
      OffsetDateTime from,
      OffsetDateTime to,
      Set<UUID> adminIds) {
    List<AdminUserEventProjection> events = new ArrayList<>();
    events.addAll(clients.findAdminActivityEvents(from, to));
    events.addAll(readings.findAdminActivityEvents(from, to));
    return events.stream().filter(event -> !adminIds.contains(event.ownerId())).toList();
  }

  private long distinctOwners(
      List<AdminUserEventProjection> events,
      OffsetDateTime from,
      OffsetDateTime to) {
    return events.stream()
        .filter(event -> !event.occurredAt().isBefore(from) && event.occurredAt().isBefore(to))
        .map(AdminUserEventProjection::ownerId)
        .distinct()
        .count();
  }

  private long countBefore(List<AdminUserEventProjection> events, OffsetDateTime instant) {
    return events.stream().filter(event -> event.occurredAt().isBefore(instant)).count();
  }

  private List<AdminAnalyticsResult.TimelinePoint> timeline(
      AdminReportingPeriod period,
      List<AdminUserEventProjection> profileEvents,
      List<AdminUserEventProjection> completedEvents) {
    Map<LocalDate, long[]> daily = new LinkedHashMap<>();
    long days = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
    for (long index = 0; index < days; index++) {
      daily.put(period.from().plusDays(index), new long[2]);
    }
    addEvents(daily, profileEvents, period, 0);
    addEvents(daily, completedEvents, period, 1);
    if ("DAY".equals(period.granularity())) {
      return daily.entrySet().stream()
          .map(entry -> new AdminAnalyticsResult.TimelinePoint(
              entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
          .toList();
    }
    Map<LocalDate, long[]> weekly = new LinkedHashMap<>();
    daily.forEach((date, counts) -> {
      long offset = ChronoUnit.DAYS.between(period.from(), date);
      LocalDate bucket = period.from().plusDays((offset / 7) * 7);
      long[] aggregate = weekly.computeIfAbsent(bucket, ignored -> new long[2]);
      aggregate[0] += counts[0];
      aggregate[1] += counts[1];
    });
    return weekly.entrySet().stream()
        .map(entry -> new AdminAnalyticsResult.TimelinePoint(
            entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
        .toList();
  }

  private void addEvents(
      Map<LocalDate, long[]> daily,
      List<AdminUserEventProjection> events,
      AdminReportingPeriod period,
      int position) {
    events.forEach(event -> {
      LocalDate date = event.occurredAt().atZoneSameInstant(period.zoneId()).toLocalDate();
      long[] counts = daily.get(date);
      if (counts != null) {
        counts[position]++;
      }
    });
  }

  private BillingSnapshot billingSnapshot(
      List<BillingAccount> accounts,
      Map<UUID, BillingPlanPrice> plans,
      OffsetDateTime at) {
    Predicate<BillingAccount> subscriber = account -> isActiveSubscriber(account, at);
    long activeTrials = accounts.stream().filter(account -> isActiveTrial(account, at)).count();
    long expiredTrials = accounts.stream()
        .filter(account -> account.getTrialEndsAt() != null && !account.getTrialEndsAt().isAfter(at))
        .filter(subscriber.negate())
        .count();
    List<BillingAccount> subscribers = accounts.stream().filter(subscriber).toList();
    long monthly = subscribers.stream().filter(account -> hasInterval(account, plans, "MONTH")).count();
    long annual = subscribers.stream().filter(account -> hasInterval(account, plans, "YEAR")).count();
    long endedTrials = accounts.stream()
        .filter(account -> account.getTrialEndsAt() != null && !account.getTrialEndsAt().isAfter(at))
        .count();
    long paidEndedTrials = accounts.stream()
        .filter(account -> account.getTrialEndsAt() != null && !account.getTrialEndsAt().isAfter(at))
        .filter(subscriber)
        .count();
    return new BillingSnapshot(
        activeTrials,
        expiredTrials,
        subscribers.size(),
        monthly,
        annual,
        ratio(paidEndedTrials, endedTrials) * 100);
  }

  private boolean isActiveTrial(BillingAccount account, OffsetDateTime at) {
    return (account.getStatus() == BillingAccountStatus.TRIALING
        || account.getStatus() == BillingAccountStatus.PAST_DUE)
        && account.getTrialEndsAt() != null
        && account.getTrialEndsAt().isAfter(at);
  }

  private boolean isActiveSubscriber(BillingAccount account, OffsetDateTime at) {
    return (account.getStatus() == BillingAccountStatus.ACTIVE
        || account.getStatus() == BillingAccountStatus.CANCEL_AT_PERIOD_END)
        && account.getCurrentPeriodEnd() != null
        && account.getCurrentPeriodEnd().isAfter(at);
  }

  private boolean hasInterval(
      BillingAccount account,
      Map<UUID, BillingPlanPrice> plans,
      String interval) {
    BillingPlanPrice plan = plans.get(account.getCurrentPlanPriceId());
    return plan != null && interval.equals(plan.getBillingInterval());
  }

  private AdminAnalyticsResult.Metric metric(
      double value,
      double previous,
      AdminAnalyticsResult.MetricUnit unit) {
    Double change = previous == 0 ? null : round(((value - previous) / previous) * 100, 1);
    return new AdminAnalyticsResult.Metric(value, previous, change, unit);
  }

  private double ratio(long numerator, long denominator) {
    return denominator == 0 ? 0 : round((double) numerator / denominator, 3);
  }

  private double round(double value, int digits) {
    double factor = Math.pow(10, digits);
    return Math.round(value * factor) / factor;
  }

  private record BillingSnapshot(
      long activeTrials,
      long expiredTrials,
      long activeSubscribers,
      long monthlySubscribers,
      long annualSubscribers,
      double trialToPaidRate) {
  }
}
