package com.arkana.service;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingPlanPrice;
import com.arkana.domain.BillingPromotionCampaign;
import com.arkana.domain.BillingPromotionCampaignPrice;
import com.arkana.domain.BillingPromotionEligibility;
import com.arkana.domain.BillingProviderEvent;
import com.arkana.domain.BillingProviderPlanMapping;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.Profile;
import com.arkana.dto.billing.BillingCheckoutResponse;
import com.arkana.dto.billing.BillingOverview;
import com.arkana.dto.billing.BillingPlanSummary;
import com.arkana.dto.billing.ChangeBillingPlanRequest;
import com.arkana.dto.billing.CreateBillingCheckoutRequest;
import com.arkana.dto.billing.SubscriptionPlanResponse;
import com.arkana.integration.PaymentProvider;
import com.arkana.repository.BillingAccessOverrideRepository;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingCheckoutRepository;
import com.arkana.repository.BillingPlanPriceRepository;
import com.arkana.repository.BillingPromotionCampaignPriceRepository;
import com.arkana.repository.BillingPromotionCampaignRepository;
import com.arkana.repository.BillingPromotionEligibilityRepository;
import com.arkana.repository.BillingProviderEventRepository;
import com.arkana.repository.BillingProviderPlanMappingRepository;
import com.arkana.repository.BillingProviderSubscriptionRepository;
import com.arkana.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingService {
  private static final String PAYMENT_PROVIDER = "ABACATEPAY";

  private final BillingAccountRepository accounts;
  private final BillingPlanPriceRepository plans;
  private final BillingProviderPlanMappingRepository planMappings;
  private final BillingProviderSubscriptionRepository subscriptions;
  private final BillingPromotionCampaignRepository campaigns;
  private final BillingPromotionCampaignPriceRepository campaignPrices;
  private final BillingPromotionEligibilityRepository eligibilities;
  private final BillingAccessOverrideRepository overrides;
  private final BillingCheckoutRepository checkouts;
  private final BillingProviderEventRepository providerEvents;
  private final ProfileRepository profiles;
  private final PaymentProvider provider;

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  @Transactional(readOnly = true)
  @Cacheable(cacheNames = "public-billing-plans")
  public List<SubscriptionPlanResponse> plans() {
    List<SubscriptionPlanResponse> promoted = campaignPlans(null);
    return promoted.isEmpty() ? defaultPlans() : mergeFallback(promoted);
  }

  @Transactional
  public BillingOverview startTrial(UUID ownerId) {
    Profile profile = profiles.findById(ownerId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found."));
    if (accounts.existsByOwnerId(ownerId)) {
      return overview(ownerId);
    }

    String emailFingerprint = fingerprint(profile.getEmail());
    if (accounts.existsByTrialEmailFingerprint(emailFingerprint)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This email has already used a trial.");
    }

    OffsetDateTime startedAt = now();
    BillingAccount account = accounts.save(BillingAccount.builder()
        .id(UUID.randomUUID())
        .ownerId(ownerId)
        .trialEmailFingerprint(emailFingerprint)
        .status("TRIALING")
        .trialStartedAt(startedAt)
        .trialEndsAt(startedAt.plusDays(14))
        .build());
    List<BillingPromotionCampaign> activeCampaigns = activeCampaigns(startedAt);
    List<BillingPromotionEligibility> granted = activeCampaigns.stream()
        .map(campaign -> BillingPromotionEligibility.builder()
            .id(UUID.randomUUID())
            .billingAccountId(account.getId())
            .campaignId(campaign.getId())
            .status("ELIGIBLE")
            .grantedAt(startedAt)
            .firstCheckoutEndsAt(startedAt.plusDays(14).plusHours(48))
            .build())
        .toList();
    eligibilities.saveAll(granted);
    return overview(ownerId);
  }

  @Transactional(readOnly = true)
  public List<SubscriptionPlanResponse> eligiblePlans(UUID ownerId) {
    BillingAccount account = account(ownerId);
    List<SubscriptionPlanResponse> promoted = campaignPlans(account.getId());
    return promoted.isEmpty() ? defaultPlans() : mergeFallback(promoted);
  }

  @Transactional(readOnly = true)
  public BillingOverview overview(UUID ownerId) {
    Optional<BillingAccount> account = accounts.findByOwnerId(ownerId);
    if (account.isEmpty()) {
      return emptyOverview();
    }
    BillingAccount value = account.get();
    OffsetDateTime currentTime = now();
    String effectiveStatus = "TRIALING".equals(value.getStatus())
        && value.getTrialEndsAt() != null
        && !value.getTrialEndsAt().isAfter(currentTime)
        ? "EXPIRED"
        : value.getStatus();
    OffsetDateTime overrideEnd = activeOverride(value, currentTime);
    return new BillingOverview(
        effectiveStatus,
        accessStatus(value, overrideEnd, currentTime),
        value.getTrialStartedAt(),
        value.getTrialEndsAt(),
        value.getCurrentPeriodStart(),
        value.getCurrentPeriodEnd(),
        value.isCancelAtPeriodEnd(),
        plan(value.getCurrentPlanPriceId()),
        plan(value.getPendingPlanPriceId()),
        overrideEnd,
        List.of("PIX_AUTOMATIC", "CARD"),
        List.of("PENDING_PAYMENT", "EXPIRED", "CANCELED").contains(effectiveStatus),
        List.of("ACTIVE", "CANCEL_AT_PERIOD_END").contains(value.getStatus()),
        "ACTIVE".equals(value.getStatus()),
        promotion(value.getId(), currentTime));
  }

  @Transactional
  public BillingCheckoutResponse checkout(UUID ownerId, UUID key, CreateBillingCheckoutRequest request) {
    if (!List.of("PIX_AUTOMATIC", "CARD").contains(request.paymentMethod())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is not available.");
    }

    BillingAccount account = account(ownerId);
    Optional<BillingCheckout> existing =
        checkouts.findByBillingAccountIdAndIdempotencyKey(account.getId(), key);
    if (existing.isPresent()) {
      return existingCheckout(existing.get(), request);
    }

    OffsetDateTime currentTime = now();
    if (account.getTrialEndsAt() != null && account.getTrialEndsAt().isAfter(currentTime)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout opens after the trial ends.");
    }

    BillingPlanPrice selectedPlan = plans.findById(request.planPriceId())
        .filter(BillingPlanPrice::isActive)
        .filter(plan -> plan.isDefaultPlan()
            || isPromotionalPlanEligible(account.getId(), plan.getId(), currentTime))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The plan is not configured or is not eligible at the payment provider."));
    BillingProviderPlanMapping mapping = planMappings
        .findByPlanPriceIdAndProvider(selectedPlan.getId(), PAYMENT_PROVIDER)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The plan is not configured or is not eligible at the payment provider."));

    UUID checkoutId = UUID.randomUUID();
    BillingCheckout checkout = checkouts.saveAndFlush(BillingCheckout.builder()
        .id(checkoutId)
        .billingAccountId(account.getId())
        .planPriceId(selectedPlan.getId())
        .paymentMethod(request.paymentMethod())
        .status("CREATING")
        .idempotencyKey(key)
        .provider(PAYMENT_PROVIDER)
        .expiresAt(currentTime.plusMinutes(30))
        .build());
    try {
      PaymentProvider.Checkout providerCheckout = provider.createCheckout(
          account.getId().toString(),
          checkoutId.toString(),
          mapping.getProviderProductId(),
          request.paymentMethod());
      checkout.pending(
          providerCheckout.providerId(),
          providerCheckout.url(),
          providerCheckout.expiresAt());
      checkouts.flush();
      return new BillingCheckoutResponse(checkoutId, providerCheckout.url(), providerCheckout.expiresAt());
    } catch (RuntimeException exception) {
      checkout.fail();
      checkouts.flush();
      throw exception;
    }
  }

  @Transactional
  public BillingOverview cancel(UUID ownerId) {
    BillingAccount account = account(ownerId);
    provider.cancel(subscription(account.getId()).getProviderSubscriptionId());
    OffsetDateTime currentTime = now();
    account.cancelAtPeriodEnd();
    eligibilities.findAllByBillingAccountIdAndStatusIn(account.getId(), List.of("ELIGIBLE", "LOCKED"))
        .forEach(eligibility -> eligibility.forfeit(currentTime));
    return overview(ownerId);
  }

  @Transactional
  public BillingOverview changePlan(UUID ownerId, ChangeBillingPlanRequest request) {
    BillingAccount account = account(ownerId);
    BillingProviderSubscription subscription = subscription(account.getId());
    BillingProviderPlanMapping mapping = planMappings
        .findByPlanPriceIdAndProvider(request.planPriceId(), PAYMENT_PROVIDER)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan is not available."));
    provider.changePlan(subscription.getProviderSubscriptionId(), mapping.getProviderProductId());
    account.schedulePlan(request.planPriceId());
    return overview(ownerId);
  }

  @Transactional
  public void webhook(byte[] raw, String signature) {
    Map<String, Object> event = provider.verifyWebhook(raw, signature);
    String eventId = (String) event.get("id");
    if (providerEvents.existsByProviderAndProviderEventId(PAYMENT_PROVIDER, eventId)) {
      return;
    }

    BillingProviderEvent providerEvent = providerEvents.save(BillingProviderEvent.builder()
        .id(UUID.randomUUID())
        .provider(PAYMENT_PROVIDER)
        .providerEventId(eventId)
        .eventType((String) event.get("event"))
        .processingStatus("RECEIVED")
        .rawPayload(String.valueOf(event.get("payload")))
        .build());
    WebhookContext context = webhookContext(
        (String) event.get("checkoutId"),
        (String) event.get("subscriptionId"));
    if (context == null) {
      providerEvent.ignore();
      return;
    }

    String eventType = (String) event.get("event");
    String status = switch (eventType) {
      case "subscription.completed", "subscription.renewed", "subscription.plan_changed" -> "ACTIVE";
      case "subscription.payment_failed" -> "PAST_DUE";
      case "subscription.cancelled" -> "CANCELED";
      default -> "TRIALING";
    };
    OffsetDateTime periodStart = (OffsetDateTime) event.get("periodStart");
    OffsetDateTime periodEnd = (OffsetDateTime) event.get("periodEnd");
    OffsetDateTime trialEnd = (OffsetDateTime) event.get("trialEnd");
    if ("ACTIVE".equals(status) && periodEnd == null) {
      periodStart = periodStart == null ? now() : periodStart;
      periodEnd = "YEAR".equals(context.interval())
          ? periodStart.plusYears(1)
          : periodStart.plusMonths(1);
    }

    BillingAccount account = accounts.findById(context.accountId()).orElseThrow();
    account.applyProviderState(status, context.planId(), periodStart, periodEnd, trialEnd);
    updateProviderSubscription(event, account.getId());
    if (context.checkoutId() != null) {
      checkouts.findById(context.checkoutId()).ifPresent(BillingCheckout::complete);
    }
    if (context.planId() != null) {
      lockPromotion(account.getId(), context.planId());
    }
    providerEvent.process();
  }

  private BillingCheckoutResponse existingCheckout(
      BillingCheckout existing,
      CreateBillingCheckoutRequest request) {
    if (!existing.getPlanPriceId().equals(request.planPriceId())
        || !existing.getPaymentMethod().equals(request.paymentMethod())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Idempotency-Key was reused with different parameters.");
    }
    if (existing.getCheckoutUrl() != null) {
      return new BillingCheckoutResponse(existing.getId(), existing.getCheckoutUrl(), existing.getExpiresAt());
    }
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout creation is in progress.");
  }

  private void updateProviderSubscription(Map<String, Object> event, UUID accountId) {
    String subscriptionId = (String) event.get("subscriptionId");
    if (subscriptionId == null) {
      return;
    }
    BillingProviderSubscription subscription = subscriptions
        .findByBillingAccountIdAndProvider(accountId, PAYMENT_PROVIDER)
        .orElseGet(() -> BillingProviderSubscription.builder()
            .id(UUID.randomUUID())
            .billingAccountId(accountId)
            .provider(PAYMENT_PROVIDER)
            .providerSubscriptionId(subscriptionId)
            .build());
    subscription.updateSubscriptionId(subscriptionId);
    subscriptions.save(subscription);
  }

  private void lockPromotion(UUID accountId, UUID planId) {
    List<UUID> campaignIds = campaignPrices.findAllByPromotionalPlanPriceId(planId).stream()
        .map(BillingPromotionCampaignPrice::getCampaignId)
        .toList();
    if (campaignIds.isEmpty()) {
      return;
    }
    OffsetDateTime lockedAt = now();
    eligibilities.findAllByBillingAccountIdAndCampaignIdInAndStatus(accountId, campaignIds, "ELIGIBLE")
        .forEach(eligibility -> eligibility.lock(lockedAt));
  }

  private BillingAccount account(UUID ownerId) {
    return accounts.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Billing account is not ready."));
  }

  private BillingProviderSubscription subscription(UUID accountId) {
    return subscriptions.findByBillingAccountIdAndProvider(accountId, PAYMENT_PROVIDER)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "No active provider subscription exists."));
  }

  private WebhookContext webhookContext(String checkoutId, String subscriptionId) {
    if (checkoutId != null) {
      try {
        Optional<BillingCheckout> checkout = checkouts.findById(UUID.fromString(checkoutId));
        if (checkout.isPresent()) {
          BillingCheckout value = checkout.get();
          String interval = plans.findById(value.getPlanPriceId())
              .map(BillingPlanPrice::getBillingInterval)
              .orElse(null);
          return new WebhookContext(
              value.getBillingAccountId(),
              value.getPlanPriceId(),
              value.getId(),
              interval);
        }
      } catch (IllegalArgumentException ignored) {
        // Continue with the provider subscription lookup.
      }
    }
    if (subscriptionId == null) {
      return null;
    }
    return subscriptions.findByProviderAndProviderSubscriptionId(PAYMENT_PROVIDER, subscriptionId)
        .map(subscription -> {
          BillingAccount account = accounts.findById(subscription.getBillingAccountId()).orElseThrow();
          UUID planId = account.getCurrentPlanPriceId();
          String interval = planId == null
              ? null
              : plans.findById(planId)
              .map(BillingPlanPrice::getBillingInterval)
              .orElse(null);
          return new WebhookContext(
              account.getId(),
              planId,
              null,
              interval);
        })
        .orElse(null);
  }

  private BillingPlanSummary plan(UUID planId) {
    if (planId == null) {
      return null;
    }
    return plans.findById(planId).map(value -> new BillingPlanSummary(
        value.getId(),
        value.getCode(),
        value.getName(),
        value.getBillingInterval(),
        value.getAmount(),
        value.getCurrency())).orElse(null);
  }

  private OffsetDateTime activeOverride(BillingAccount account, OffsetDateTime currentTime) {
    return overrides
        .findFirstByBillingAccountIdAndStartsAtLessThanEqualAndEndsAtGreaterThanAndRevokedAtIsNullOrderByEndsAtDesc(
            account.getId(),
            currentTime,
            currentTime)
        .map(value -> value.getEndsAt())
        .orElse(account.getOverrideEndsAt());
  }

  private Object promotion(UUID accountId, OffsetDateTime currentTime) {
    return eligibilities.findAllByBillingAccountIdOrderByGrantedAtDesc(accountId).stream()
        .findFirst()
        .flatMap(eligibility -> campaigns.findById(eligibility.getCampaignId())
            .map(campaign -> promotion(campaign, eligibility, currentTime)))
        .orElse(null);
  }

  private Map<String, Object> promotion(
      BillingPromotionCampaign campaign,
      BillingPromotionEligibility eligibility,
      OffsetDateTime currentTime) {
    String status = "ELIGIBLE".equals(eligibility.getStatus())
        && !eligibility.getFirstCheckoutEndsAt().isAfter(currentTime)
        ? "EXPIRED"
        : eligibility.getStatus();
    LinkedHashMap<String, Object> value = new LinkedHashMap<>();
    value.put("code", campaign.getCode());
    value.put("name", campaign.getName());
    value.put("status", status);
    value.put("campaignEndsAt", campaign.getEndsAt());
    value.put("firstCheckoutEndsAt", eligibility.getFirstCheckoutEndsAt());
    value.put("lockedAt", eligibility.getLockedAt());
    return value;
  }

  private String accessStatus(
      BillingAccount account,
      OffsetDateTime overrideEnd,
      OffsetDateTime currentTime) {
    boolean trial = "TRIALING".equals(account.getStatus())
        && account.getTrialEndsAt() != null
        && account.getTrialEndsAt().isAfter(currentTime);
    boolean subscription = List.of("ACTIVE", "CANCEL_AT_PERIOD_END").contains(account.getStatus())
        && account.getCurrentPeriodEnd() != null
        && account.getCurrentPeriodEnd().isAfter(currentTime);
    boolean override = overrideEnd != null && overrideEnd.isAfter(currentTime);
    return trial || subscription || override ? "ACTIVE" : "BLOCKED";
  }

  private List<SubscriptionPlanResponse> defaultPlans() {
    return plans.findAllByActiveTrueAndDefaultPlanTrueOrderByAmountAsc().stream()
        .map(plan -> planResponse(plan, plan.getCompareAtAmount(), null))
        .toList();
  }

  private List<SubscriptionPlanResponse> campaignPlans(UUID accountId) {
    OffsetDateTime currentTime = now();
    List<CampaignOffer> offers = accountId == null
        ? publicOffers(currentTime)
        : eligibleOffers(accountId, currentTime);
    if (offers.isEmpty()) {
      return List.of();
    }
    Map<UUID, BillingPlanPrice> pricesById = plans.findAllById(offers.stream()
            .flatMap(offer -> offer.prices().stream())
            .map(BillingPromotionCampaignPrice::getPromotionalPlanPriceId)
            .toList())
        .stream()
        .collect(Collectors.toMap(BillingPlanPrice::getId, Function.identity()));
    Map<UUID, BillingPlanPrice> comparisonById = plans.findAllById(offers.stream()
            .flatMap(offer -> offer.prices().stream())
            .map(BillingPromotionCampaignPrice::getCompareAtPlanPriceId)
            .toList())
        .stream()
        .collect(Collectors.toMap(BillingPlanPrice::getId, Function.identity()));

    return offers.stream()
        .flatMap(offer -> offer.prices().stream().map(price -> {
          BillingPlanPrice promoted = pricesById.get(price.getPromotionalPlanPriceId());
          BillingPlanPrice comparison = comparisonById.get(price.getCompareAtPlanPriceId());
          if (promoted == null || comparison == null || !promoted.isActive()) {
            return null;
          }
          LinkedHashMap<String, Object> promotion = new LinkedHashMap<>();
          promotion.put("code", offer.campaign().getCode());
          promotion.put("name", offer.campaign().getName());
          promotion.put("campaignEndsAt", offer.campaign().getEndsAt());
          promotion.put("offerEndsAt", offer.offerEndsAt());
          promotion.put("retentionPolicy", offer.campaign().getRetentionPolicy());
          return planResponse(promoted, comparison.getAmount(), promotion);
        }))
        .filter(java.util.Objects::nonNull)
        .sorted(Comparator.comparingInt(SubscriptionPlanResponse::amount))
        .toList();
  }

  private List<CampaignOffer> publicOffers(OffsetDateTime currentTime) {
    List<BillingPromotionCampaign> active = activeCampaigns(currentTime);
    return offers(active, Map.of());
  }

  private List<CampaignOffer> eligibleOffers(UUID accountId, OffsetDateTime currentTime) {
    List<BillingPromotionEligibility> activeEligibilities =
        eligibilities.findAllByBillingAccountIdAndStatusIn(accountId, List.of("ELIGIBLE", "LOCKED")).stream()
            .filter(eligibility -> eligibility.getFirstCheckoutEndsAt().isAfter(currentTime))
            .toList();
    Map<UUID, BillingPromotionEligibility> eligibilityByCampaign = activeEligibilities.stream()
        .collect(Collectors.toMap(BillingPromotionEligibility::getCampaignId, Function.identity()));
    List<BillingPromotionCampaign> eligibleCampaigns = campaigns.findAllById(eligibilityByCampaign.keySet());
    return offers(eligibleCampaigns, eligibilityByCampaign);
  }

  private List<CampaignOffer> offers(
      Collection<BillingPromotionCampaign> selectedCampaigns,
      Map<UUID, BillingPromotionEligibility> eligibilityByCampaign) {
    List<UUID> campaignIds = selectedCampaigns.stream()
        .map(BillingPromotionCampaign::getId)
        .toList();
    Map<UUID, List<BillingPromotionCampaignPrice>> pricesByCampaign = campaignPrices
        .findAllByCampaignIdIn(campaignIds)
        .stream()
        .collect(Collectors.groupingBy(BillingPromotionCampaignPrice::getCampaignId));
    return selectedCampaigns.stream()
        .map(campaign -> {
          BillingPromotionEligibility eligibility = eligibilityByCampaign.get(campaign.getId());
          OffsetDateTime offerEnd = eligibility == null
              ? campaign.getEndsAt()
              : eligibility.getFirstCheckoutEndsAt();
          return new CampaignOffer(
              campaign,
              pricesByCampaign.getOrDefault(campaign.getId(), List.of()),
              offerEnd);
        })
        .toList();
  }

  private List<BillingPromotionCampaign> activeCampaigns(OffsetDateTime currentTime) {
    return campaigns.findAllByStatusAndStartsAtLessThanEqualAndEndsAtGreaterThan(
        "ACTIVE",
        currentTime,
        currentTime);
  }

  private boolean isPromotionalPlanEligible(UUID accountId, UUID planId, OffsetDateTime currentTime) {
    Set<UUID> campaignIds = campaignPrices.findAllByPromotionalPlanPriceId(planId).stream()
        .map(BillingPromotionCampaignPrice::getCampaignId)
        .collect(Collectors.toSet());
    if (campaignIds.isEmpty()) {
      return false;
    }
    return eligibilities.findAllByBillingAccountIdAndStatusIn(accountId, List.of("ELIGIBLE", "LOCKED")).stream()
        .anyMatch(eligibility -> campaignIds.contains(eligibility.getCampaignId())
            && eligibility.getFirstCheckoutEndsAt().isAfter(currentTime));
  }

  private SubscriptionPlanResponse planResponse(
      BillingPlanPrice plan,
      Integer compareAtAmount,
      Object promotion) {
    Double savings = compareAtAmount == null
        ? null
        : Math.round((1d - plan.getAmount() / (double) compareAtAmount) * 1000d) / 10d;
    return new SubscriptionPlanResponse(
        plan.getId(),
        plan.getCode(),
        plan.getName(),
        plan.getBillingInterval(),
        plan.getAmount(),
        compareAtAmount,
        plan.getCurrency(),
        plan.getTrialDays(),
        savings,
        List.copyOf(plan.getAvailablePaymentMethods()),
        promotion);
  }

  private List<SubscriptionPlanResponse> mergeFallback(List<SubscriptionPlanResponse> promoted) {
    Set<String> intervals = promoted.stream()
        .map(SubscriptionPlanResponse::interval)
        .collect(Collectors.toSet());
    List<SubscriptionPlanResponse> result = new ArrayList<>(promoted);
    defaultPlans().stream()
        .filter(plan -> !intervals.contains(plan.interval()))
        .forEach(result::add);
    return result;
  }

  private BillingOverview emptyOverview() {
    return new BillingOverview(
        "PENDING_PAYMENT",
        "BLOCKED",
        null,
        null,
        null,
        null,
        false,
        null,
        null,
        null,
        List.of("PIX_AUTOMATIC", "CARD"),
        false,
        false,
        false,
        null);
  }

  private String fingerprint(String email) {
    try {
      byte[] normalized = email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private record WebhookContext(UUID accountId, UUID planId, UUID checkoutId, String interval) {
  }

  private record CampaignOffer(
      BillingPromotionCampaign campaign,
      List<BillingPromotionCampaignPrice> prices,
      OffsetDateTime offerEndsAt) {
  }
}
