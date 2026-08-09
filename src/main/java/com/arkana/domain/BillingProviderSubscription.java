package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "billing_provider_subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingProviderSubscription {
  @Id
  private UUID id;
  @Column(name = "billing_account_id", nullable = false)
  private UUID billingAccountId;
  @Column(nullable = false, length = 32)
  private String provider;
  @Column(name = "provider_subscription_id", nullable = false, length = 200)
  private String providerSubscriptionId;

  public BillingProviderSubscription(UUID billingAccountId, String provider, String subscriptionId) {
    id = UUID.randomUUID();
    this.billingAccountId = billingAccountId;
    this.provider = provider;
    this.providerSubscriptionId = subscriptionId;
  }

  public void updateSubscriptionId(String subscriptionId) {
    providerSubscriptionId = subscriptionId;
  }

  public String getProviderSubscriptionId() {
    return providerSubscriptionId;
  }
}
