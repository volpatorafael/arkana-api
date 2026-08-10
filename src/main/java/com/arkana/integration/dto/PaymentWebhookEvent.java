package com.arkana.integration.dto;

import com.arkana.domain.BillingProviderEventType;

import java.time.OffsetDateTime;
import com.arkana.domain.BillingPaymentMethod;

public record PaymentWebhookEvent(
    String id,
    BillingProviderEventType eventType,
    String rawPayload,
    String subscriptionId,
    String productId,
    String checkoutId,
    String providerCheckoutId,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    OffsetDateTime trialEnd,
    BillingPaymentMethod paymentMethod) {

  public PaymentWebhookEvent(
      String id,
      BillingProviderEventType eventType,
      String rawPayload,
      String subscriptionId,
      String productId,
      String checkoutId,
      String providerCheckoutId,
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd,
      OffsetDateTime trialEnd) {
    this(
        id,
        eventType,
        rawPayload,
        subscriptionId,
        productId,
        checkoutId,
        providerCheckoutId,
        periodStart,
        periodEnd,
        trialEnd,
        null);
  }

  public PaymentWebhookEvent(
      String id,
      BillingProviderEventType eventType,
      String rawPayload,
      String subscriptionId,
      String productId,
      String checkoutId,
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd,
      OffsetDateTime trialEnd) {
    this(
        id,
        eventType,
        rawPayload,
        subscriptionId,
        productId,
        checkoutId,
        null,
        periodStart,
        periodEnd,
        trialEnd,
        null);
  }
}
