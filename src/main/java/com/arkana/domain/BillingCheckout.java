package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "billing_checkouts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingCheckout {
  @Id
  private UUID id;
  @Column(name = "billing_account_id", nullable = false)
  private UUID billingAccountId;
  @Column(name = "plan_price_id", nullable = false)
  private UUID planPriceId;
  @Column(name = "payment_method", nullable = false, length = 32)
  private String paymentMethod;
  @Column(nullable = false, length = 16)
  private String status;
  @Column(name = "idempotency_key", nullable = false)
  private UUID idempotencyKey;
  @Column(nullable = false, length = 32)
  private String provider;
  @Column(name = "provider_checkout_id", length = 200)
  private String providerCheckoutId;
  @Column(name = "checkout_url", length = 2000)
  private String checkoutUrl;
  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public BillingCheckout(
      UUID id,
      UUID billingAccountId,
      UUID planPriceId,
      String paymentMethod,
      String status,
      UUID idempotencyKey,
      String provider,
      OffsetDateTime expiresAt) {
    this.id = id;
    this.billingAccountId = billingAccountId;
    this.planPriceId = planPriceId;
    this.paymentMethod = paymentMethod;
    this.status = status;
    this.idempotencyKey = idempotencyKey;
    this.provider = provider;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void createTimestamp() {
    createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public void pending(String providerCheckoutId, String checkoutUrl, OffsetDateTime expiresAt) {
    status = "PENDING";
    this.providerCheckoutId = providerCheckoutId;
    this.checkoutUrl = checkoutUrl;
    this.expiresAt = expiresAt;
  }

  public void fail() {
    status = "FAILED";
  }

  public void complete() {
    status = "COMPLETED";
  }

  public UUID getBillingAccountId() {
    return billingAccountId;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }
}
