package com.arkana.service;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingPlanPrice;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderCharge;
import com.arkana.domain.BillingProviderChargeStatus;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.BillingProviderSubscriptionStatus;
import com.arkana.integration.efi.EfiProvider;
import com.arkana.repository.BillingPlanPriceRepository;
import com.arkana.repository.BillingProviderChargeRepository;
import com.arkana.repository.BillingProviderSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EfiPixChargeScheduler {
  private final BillingProviderSubscriptionRepository subscriptions;
  private final BillingProviderChargeRepository charges;
  private final BillingPlanPriceRepository plans;
  private final EfiProvider efi;
  private final Clock clock;

  @Scheduled(cron = "${arkana.efi.pix-charge-cron:0 17 * * * *}")
  @Transactional
  public void ensureUpcomingCharges() {
    OffsetDateTime horizon = OffsetDateTime.now(clock).plusDays(7);
    List<BillingProviderSubscription> candidates = subscriptions
        .findAllByProviderAndPaymentMethodAndStatusIn(
            BillingProvider.EFI,
            BillingPaymentMethod.PIX_AUTOMATIC,
            List.of(
                BillingProviderSubscriptionStatus.SCHEDULED,
                BillingProviderSubscriptionStatus.ACTIVE));
    for (BillingProviderSubscription subscription : candidates) {
      OffsetDateTime nextChargeAt = subscription.getNextChargeAt();
      if (nextChargeAt == null || nextChargeAt.isAfter(horizon)) {
        continue;
      }
      OffsetDateTime dueAt = nextChargeAt.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
      if (charges.findByProviderSubscriptionIdAndDueAt(subscription.getId(), dueAt).isPresent()) {
        continue;
      }
      BillingPlanPrice plan = plans.findById(subscription.getPlanPriceId()).orElse(null);
      if (plan == null) {
        log.error("Could not schedule Efí Pix charge because the plan is missing. subscriptionId={}",
            subscription.getId());
        continue;
      }
      String transactionId = transactionId(subscription.getId(), dueAt);
      efi.createRecurringPixCharge(
          subscription.getProviderSubscriptionId(),
          transactionId,
          dueAt,
          plan.getAmount());
      charges.save(BillingProviderCharge.builder()
          .id(UUID.randomUUID())
          .providerSubscriptionId(subscription.getId())
          .provider(BillingProvider.EFI)
          .providerChargeId(transactionId)
          .dueAt(dueAt)
          .amount(plan.getAmount())
          .status(BillingProviderChargeStatus.CREATED)
          .build());
    }
  }

  private String transactionId(UUID subscriptionId, OffsetDateTime dueAt) {
    try {
      String source = subscriptionId + ":" + dueAt.toLocalDate();
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(source.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}

