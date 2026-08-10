package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(name = "billing_provider_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingProviderSubscription {
  @Id
  private UUID id;
  @Column(name = "billing_account_id", nullable = false)
  private UUID billingAccountId;
  @Column(nullable = false, length = 32)
  @Enumerated(EnumType.STRING)
  private BillingProvider provider;
  @Column(name = "provider_subscription_id", nullable = false, length = 200)
  private String providerSubscriptionId;
  @Column(name = "plan_price_id", nullable = false)
  private UUID planPriceId;
  @Column(nullable = false, length = 32)
  @Enumerated(EnumType.STRING)
  private BillingProviderSubscriptionStatus status;
  @Column(name = "next_charge_at")
  private OffsetDateTime nextChargeAt;

  public void updateSubscriptionId(String subscriptionId) {
    providerSubscriptionId = subscriptionId;
  }

  public void schedule(UUID planId, OffsetDateTime chargeAt) {
    planPriceId = planId;
    nextChargeAt = chargeAt;
    status = BillingProviderSubscriptionStatus.SCHEDULED;
  }

  public void activate(UUID planId, OffsetDateTime chargeAt) {
    planPriceId = planId;
    nextChargeAt = chargeAt;
    status = BillingProviderSubscriptionStatus.ACTIVE;
  }

  public void cancel() {
    status = BillingProviderSubscriptionStatus.CANCELED;
    nextChargeAt = null;
  }

}
