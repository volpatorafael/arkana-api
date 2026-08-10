package com.arkana.service;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingCheckoutStatus;
import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingPlanPrice;
import com.arkana.domain.BillingPromotionCampaign;
import com.arkana.domain.BillingPromotionCampaignPrice;
import com.arkana.domain.BillingPromotionEligibility;
import com.arkana.domain.BillingPromotionEligibilityStatus;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderEvent;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.domain.BillingProviderPlanMapping;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.Profile;
import com.arkana.dto.billing.BillingCheckoutResponse;
import com.arkana.dto.billing.BillingOverview;
import com.arkana.dto.billing.BillingPlanSummary;
import com.arkana.dto.billing.BillingPromotionResponse;
import com.arkana.dto.billing.ChangeBillingPlanRequest;
import com.arkana.dto.billing.CreateBillingCheckoutRequest;
import com.arkana.dto.billing.SubscriptionPlanResponse;
import com.arkana.dto.billing.PlanPromotionResponse;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.PaymentProviderRegistry;
import com.arkana.integration.dto.PaymentWebhookEvent;
import com.arkana.mapper.BillingAccountMapper;
import com.arkana.mapper.BillingCheckoutMapper;
import com.arkana.mapper.BillingPlanPriceMapper;
import com.arkana.mapper.BillingPromotionMapper;
import com.arkana.mapper.BillingOverviewMappingSource;
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
  private final PaymentProviderRegistry providerRegistry;
  private final BillingAccountMapper accountMapper;
  private final BillingCheckoutMapper checkoutMapper;
  private final BillingPlanPriceMapper planMapper;
  private final BillingPromotionMapper promotionMapper;

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
        .status(BillingAccountStatus.TRIALING)
        .trialStartedAt(startedAt)
        .trialEndsAt(startedAt.plusDays(14))
        .build());
    List<BillingPromotionCampaign> activeCampaigns = activeCampaigns(startedAt);
    List<BillingPromotionEligibility> granted = activeCampaigns.stream()
        .map(campaign -> BillingPromotionEligibility.builder()
            .id(UUID.randomUUID())
            .billingAccountId(account.getId())
            .campaignId(campaign.getId())
            .status(BillingPromotionEligibilityStatus.ELIGIBLE)
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
    BillingAccountStatus effectiveStatus;
    if (value.getStatus() == BillingAccountStatus.TRIALING
        && value.getTrialEndsAt() != null
        && !value.getTrialEndsAt().isAfter(currentTime)) {
      effectiveStatus = BillingAccountStatus.EXPIRED;
    } else if (value.getStatus() == BillingAccountStatus.CANCEL_AT_PERIOD_END
        && value.getCurrentPeriodEnd() != null
        && !value.getCurrentPeriodEnd().isAfter(currentTime)) {
      effectiveStatus = BillingAccountStatus.CANCELED;
    } else {
      effectiveStatus = value.getStatus();
    }
    OffsetDateTime overrideEnd = activeOverride(value, currentTime);
    return accountMapper.toOverview(
        value,
        effectiveStatus.name(),
        accessStatus(value, overrideEnd, currentTime),
        plan(value.getCurrentPlanPriceId()),
        plan(value.getPendingPlanPriceId()),
        overrideEnd,
        availablePaymentMethods(),
        List.of(
            BillingAccountStatus.PENDING_PAYMENT,
            BillingAccountStatus.EXPIRED,
            BillingAccountStatus.CANCELED).contains(effectiveStatus),
        value.getStatus() == BillingAccountStatus.ACTIVE,
        value.getStatus() == BillingAccountStatus.ACTIVE,
        promotion(value.getId(), currentTime));
  }

  @Transactional
  public BillingCheckoutResponse checkout(UUID ownerId, UUID key, CreateBillingCheckoutRequest request) {
    BillingPaymentMethod paymentMethod = paymentMethod(request.paymentMethod());
    BillingProvider selectedProvider = providerRegistry.selectedProvider();
    PaymentProvider provider = providerRegistry.selected();
    if (!provider.supportedPaymentMethods().contains(paymentMethod)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is not available.");
    }

    BillingAccount account = account(ownerId);
    Optional<BillingCheckout> existing =
        checkouts.findByBillingAccountIdAndIdempotencyKey(account.getId(), key);
    if (existing.isPresent()) {
      return existingCheckout(existing.get(), request.planPriceId(), paymentMethod);
    }

    OffsetDateTime currentTime = now();
    if (account.getTrialEndsAt() != null && account.getTrialEndsAt().isAfter(currentTime)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout opens after the trial ends.");
    }

    BillingPlanPrice selectedPlan = plans.findById(request.planPriceId())
        .filter(BillingPlanPrice::isActive)
        .filter(plan -> plan.getAvailablePaymentMethods().contains(paymentMethod.name()))
        .filter(plan -> plan.isDefaultPlan()
            || isPromotionalPlanEligible(account.getId(), plan.getId(), currentTime))
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The plan is not configured or is not eligible at the payment provider."));
    String providerProductId = provider.requiresPlanMapping()
        ? planMappings.findByPlanPriceIdAndProvider(selectedPlan.getId(), selectedProvider)
        .map(BillingProviderPlanMapping::getProviderProductId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "The plan is not configured or is not eligible at the payment provider."))
        : null;

    UUID checkoutId = UUID.randomUUID();
    BillingCheckout checkout = checkouts.saveAndFlush(BillingCheckout.builder()
        .id(checkoutId)
        .billingAccountId(account.getId())
        .planPriceId(selectedPlan.getId())
        .paymentMethod(paymentMethod)
        .status(BillingCheckoutStatus.CREATING)
        .idempotencyKey(key)
        .provider(selectedProvider)
        .expiresAt(currentTime.plusMinutes(30))
        .build());
    try {
      PaymentProvider.Checkout providerCheckout = provider.createCheckout(
          new PaymentProvider.CreateCheckout(
              account.getId().toString(),
              checkoutId.toString(),
              paymentMethod,
              providerPlan(selectedPlan, providerProductId)));
      checkout.pending(
          providerCheckout.providerId(),
          providerCheckout.url(),
          providerCheckout.expiresAt());
      checkouts.flush();
      return checkoutMapper.toResponse(
          checkoutId,
          providerCheckout.url(),
          providerCheckout.expiresAt());
    } catch (RuntimeException exception) {
      checkout.fail();
      checkouts.flush();
      throw exception;
    }
  }

  @Transactional
  public BillingOverview cancel(UUID ownerId) {
    BillingAccount account = account(ownerId);
    BillingProvider activeProvider = activeProvider(account);
    BillingProviderSubscription subscription = subscription(account.getId(), activeProvider);
    providerRegistry.get(activeProvider).cancel(subscription.getProviderSubscriptionId());
    OffsetDateTime currentTime = now();
    account.cancelAtPeriodEnd();
    eligibilities.findAllByBillingAccountIdAndStatusIn(
            account.getId(),
            List.of(
                BillingPromotionEligibilityStatus.ELIGIBLE,
                BillingPromotionEligibilityStatus.LOCKED))
        .forEach(eligibility -> eligibility.forfeit(currentTime));
    return overview(ownerId);
  }

  @Transactional
  public BillingOverview changePlan(UUID ownerId, ChangeBillingPlanRequest request) {
    BillingAccount account = account(ownerId);
    BillingProvider activeProvider = activeProvider(account);
    PaymentProvider provider = providerRegistry.get(activeProvider);
    BillingProviderSubscription subscription = subscription(account.getId(), activeProvider);
    BillingPlanPrice selectedPlan = plans.findById(request.planPriceId())
        .filter(BillingPlanPrice::isActive)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan is not available."));
    String providerProductId = provider.requiresPlanMapping()
        ? planMappings.findByPlanPriceIdAndProvider(request.planPriceId(), activeProvider)
        .map(BillingProviderPlanMapping::getProviderProductId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan is not available."))
        : null;
    provider.changePlan(new PaymentProvider.ChangePlan(
        subscription.getProviderSubscriptionId(),
        providerPlan(selectedPlan, providerProductId)));
    account.schedulePlan(request.planPriceId());
    return overview(ownerId);
  }

  @Transactional
  public void webhook(BillingProvider paymentProvider, byte[] raw, String signature) {
    PaymentProvider provider = providerRegistry.get(paymentProvider);
    PaymentWebhookEvent event = provider.verifyWebhook(raw, signature);
    String eventId = event.id();
    if (providerEvents.existsByProviderAndProviderEventId(paymentProvider, eventId)) {
      return;
    }

    BillingProviderEventType eventType = event.eventType();
    BillingProviderEvent providerEvent = providerEvents.save(BillingProviderEvent.builder()
        .id(UUID.randomUUID())
        .provider(paymentProvider)
        .providerEventId(eventId)
        .eventType(eventType)
        .processingStatus("RECEIVED")
        .rawPayload(event.rawPayload())
        .build());
    if (eventType == BillingProviderEventType.IGNORED) {
      providerEvent.ignore();
      return;
    }
    WebhookContext context = webhookContext(
        paymentProvider,
        event.checkoutId(),
        event.providerCheckoutId(),
        event.subscriptionId(),
        event.productId(),
        eventType == BillingProviderEventType.PLAN_CHANGED);
    if (context == null) {
      providerEvent.ignore();
      return;
    }

    BillingAccount account = accounts.findById(context.accountId()).orElseThrow();
    updateProviderSubscription(paymentProvider, event, account.getId());
    if (eventType == BillingProviderEventType.SUBSCRIPTION_LINKED) {
      providerEvent.process();
      return;
    }
    if (eventType == BillingProviderEventType.PAYMENT_FAILED
        && context.checkoutId() != null
        && account.getCurrentProvider() == null) {
      checkouts.findById(context.checkoutId()).ifPresent(BillingCheckout::fail);
      providerEvent.process();
      return;
    }

    BillingAccountStatus status = switch (eventType) {
      case COMPLETED, RENEWED, PLAN_CHANGED ->
          BillingAccountStatus.ACTIVE;
      case PAYMENT_FAILED -> BillingAccountStatus.PAST_DUE;
      case CANCELED -> account.getCurrentPeriodEnd() != null
          && account.getCurrentPeriodEnd().isAfter(now())
          ? BillingAccountStatus.CANCEL_AT_PERIOD_END
          : BillingAccountStatus.CANCELED;
      default -> BillingAccountStatus.TRIALING;
    };
    OffsetDateTime periodStart = event.periodStart();
    OffsetDateTime periodEnd = event.periodEnd();
    OffsetDateTime trialEnd = event.trialEnd();
    if (status == BillingAccountStatus.ACTIVE && periodEnd == null) {
      periodStart = periodStart == null ? now() : periodStart;
      periodEnd = "YEAR".equals(context.interval())
          ? periodStart.plusYears(1)
          : periodStart.plusMonths(1);
    }
    if (status == BillingAccountStatus.CANCEL_AT_PERIOD_END) {
      periodStart = periodStart == null ? account.getCurrentPeriodStart() : periodStart;
      periodEnd = periodEnd == null ? account.getCurrentPeriodEnd() : periodEnd;
    }

    account.applyProviderState(status, context.planId(), periodStart, periodEnd, trialEnd);
    if (status == BillingAccountStatus.ACTIVE) {
      account.useProvider(paymentProvider);
    }
    if (context.checkoutId() != null
        && List.of(
            BillingProviderEventType.COMPLETED,
            BillingProviderEventType.RENEWED).contains(eventType)) {
      checkouts.findById(context.checkoutId()).ifPresent(BillingCheckout::complete);
    }
    if (context.planId() != null) {
      lockPromotion(account.getId(), context.planId());
    }
    providerEvent.process();
  }

  private BillingCheckoutResponse existingCheckout(
      BillingCheckout existing,
      UUID planPriceId,
      BillingPaymentMethod paymentMethod) {
    if (!existing.getPlanPriceId().equals(planPriceId)
        || existing.getPaymentMethod() != paymentMethod) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Idempotency-Key was reused with different parameters.");
    }
    if (existing.getCheckoutUrl() != null) {
      return checkoutMapper.toResponse(existing);
    }
    throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout creation is in progress.");
  }

  private BillingPaymentMethod paymentMethod(String value) {
    try {
      return BillingPaymentMethod.valueOf(value);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is not available.");
    }
  }

  private void updateProviderSubscription(
      BillingProvider paymentProvider,
      PaymentWebhookEvent event,
      UUID accountId) {
    String subscriptionId = event.subscriptionId();
    if (subscriptionId == null) {
      return;
    }
    BillingProviderSubscription subscription = subscriptions
        .findByBillingAccountIdAndProvider(accountId, paymentProvider)
        .orElseGet(() -> BillingProviderSubscription.builder()
            .id(UUID.randomUUID())
            .billingAccountId(accountId)
            .provider(paymentProvider)
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
    eligibilities.findAllByBillingAccountIdAndCampaignIdInAndStatus(
            accountId,
            campaignIds,
            BillingPromotionEligibilityStatus.ELIGIBLE)
        .forEach(eligibility -> eligibility.lock(lockedAt));
  }

  private BillingAccount account(UUID ownerId) {
    return accounts.findByOwnerId(ownerId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Billing account is not ready."));
  }

  private BillingProviderSubscription subscription(UUID accountId, BillingProvider provider) {
    return subscriptions.findByBillingAccountIdAndProvider(accountId, provider)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "No active provider subscription exists."));
  }

  private WebhookContext webhookContext(
      BillingProvider paymentProvider,
      String checkoutId,
      String providerCheckoutId,
      String subscriptionId,
      String providerProductId,
      boolean requireMappedProduct) {
    if (checkoutId != null) {
      try {
        Optional<BillingCheckout> checkout = checkouts.findById(UUID.fromString(checkoutId));
        if (checkout.isPresent() && checkout.get().getProvider() == paymentProvider) {
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
    if (providerCheckoutId != null) {
      Optional<BillingCheckout> checkout = checkouts.findByProviderAndProviderCheckoutId(
          paymentProvider,
          providerCheckoutId);
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
    }
    if (subscriptionId == null) {
      return null;
    }
    return subscriptions.findByProviderAndProviderSubscriptionId(paymentProvider, subscriptionId)
        .map(subscription -> {
          BillingAccount account = accounts.findById(subscription.getBillingAccountId()).orElseThrow();
          UUID planId = webhookPlanId(
              paymentProvider,
              account,
              providerProductId,
              requireMappedProduct);
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

  private UUID webhookPlanId(
      BillingProvider paymentProvider,
      BillingAccount account,
      String providerProductId,
      boolean planChanged) {
    if (providerProductId == null) {
      if (planChanged && account.getPendingPlanPriceId() != null) {
        return account.getPendingPlanPriceId();
      }
      return account.getCurrentPlanPriceId();
    }
    return planMappings.findByProviderAndProviderProductId(paymentProvider, providerProductId)
        .map(BillingProviderPlanMapping::getPlanPriceId)
        .orElseGet(() -> {
          if (planChanged) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The provider plan is not configured.");
          }
          return account.getCurrentPlanPriceId();
        });
  }

  private BillingPlanSummary plan(UUID planId) {
    if (planId == null) {
      return null;
    }
    return plans.findById(planId).map(planMapper::toSummary).orElse(null);
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

  private BillingPromotionResponse promotion(UUID accountId, OffsetDateTime currentTime) {
    return eligibilities.findAllByBillingAccountIdOrderByGrantedAtDesc(accountId).stream()
        .findFirst()
        .flatMap(eligibility -> campaigns.findById(eligibility.getCampaignId())
            .map(campaign -> promotion(campaign, eligibility, currentTime)))
        .orElse(null);
  }

  private BillingPromotionResponse promotion(
      BillingPromotionCampaign campaign,
      BillingPromotionEligibility eligibility,
      OffsetDateTime currentTime) {
    BillingPromotionEligibilityStatus status =
        eligibility.getStatus() == BillingPromotionEligibilityStatus.ELIGIBLE
        && !eligibility.getFirstCheckoutEndsAt().isAfter(currentTime)
        ? BillingPromotionEligibilityStatus.EXPIRED
        : eligibility.getStatus();
    return promotionMapper.toResponse(campaign, eligibility, status.name());
  }

  private String accessStatus(
      BillingAccount account,
      OffsetDateTime overrideEnd,
      OffsetDateTime currentTime) {
    boolean trial = account.getStatus() == BillingAccountStatus.TRIALING
        && account.getTrialEndsAt() != null
        && account.getTrialEndsAt().isAfter(currentTime);
    boolean subscription = List.of(
        BillingAccountStatus.ACTIVE,
        BillingAccountStatus.CANCEL_AT_PERIOD_END).contains(account.getStatus())
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
          PlanPromotionResponse promotion =
              promotionMapper.toOfferResponse(offer.campaign(), offer.offerEndsAt());
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
        eligibilities.findAllByBillingAccountIdAndStatusIn(
                accountId,
                List.of(
                    BillingPromotionEligibilityStatus.ELIGIBLE,
                    BillingPromotionEligibilityStatus.LOCKED))
            .stream()
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
    return eligibilities.findAllByBillingAccountIdAndStatusIn(
            accountId,
            List.of(
                BillingPromotionEligibilityStatus.ELIGIBLE,
                BillingPromotionEligibilityStatus.LOCKED))
        .stream()
        .anyMatch(eligibility -> campaignIds.contains(eligibility.getCampaignId())
            && eligibility.getFirstCheckoutEndsAt().isAfter(currentTime));
  }

  private SubscriptionPlanResponse planResponse(
      BillingPlanPrice plan,
      Integer compareAtAmount,
      PlanPromotionResponse promotion) {
    Double savings = compareAtAmount == null
        ? null
        : Math.round((1d - plan.getAmount() / (double) compareAtAmount) * 1000d) / 10d;
    return planMapper.toResponse(
        plan,
        compareAtAmount,
        savings,
        availablePaymentMethods(plan),
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
    return accountMapper.toEmptyOverview(
        new BillingOverviewMappingSource(availablePaymentMethods()));
  }

  private List<String> availablePaymentMethods() {
    Set<BillingPaymentMethod> supported = providerRegistry.selected().supportedPaymentMethods();
    return List.of(BillingPaymentMethod.PIX_AUTOMATIC, BillingPaymentMethod.CARD).stream()
        .filter(supported::contains)
        .map(Enum::name)
        .toList();
  }

  private List<String> availablePaymentMethods(BillingPlanPrice plan) {
    Set<BillingPaymentMethod> supported = providerRegistry.selected().supportedPaymentMethods();
    return plan.getAvailablePaymentMethods().stream()
        .map(BillingPaymentMethod::valueOf)
        .filter(supported::contains)
        .map(Enum::name)
        .toList();
  }

  private BillingProvider activeProvider(BillingAccount account) {
    if (account.getCurrentProvider() != null) {
      return account.getCurrentProvider();
    }
    return subscriptions.findFirstByBillingAccountId(account.getId())
        .map(BillingProviderSubscription::getProvider)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "No active provider subscription exists."));
  }

  private PaymentProvider.Plan providerPlan(BillingPlanPrice plan, String providerProductId) {
    return new PaymentProvider.Plan(
        plan.getId().toString(),
        plan.getCode(),
        plan.getName(),
        plan.getBillingInterval(),
        plan.getAmount(),
        plan.getCurrency(),
        providerProductId);
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
