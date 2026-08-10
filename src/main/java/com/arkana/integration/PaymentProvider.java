package com.arkana.integration;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;

import java.time.OffsetDateTime;
import java.util.Set;

public interface PaymentProvider {
  BillingProvider provider();

  Set<BillingPaymentMethod> supportedPaymentMethods();

  boolean requiresPlanMapping();

  Checkout createCheckout(CreateCheckout command);

  void cancel(String subscriptionId);

  void changePlan(ChangePlan command);

  PaymentWebhookEvent verifyWebhook(byte[] rawBody, String signature);

  record Checkout(String providerId, String url, OffsetDateTime expiresAt) {
  }

  record Plan(
      String id,
      String code,
      String name,
      String interval,
      int amount,
      String currency,
      String providerProductId) {
  }

  record CreateCheckout(
      String accountId,
      String checkoutId,
      BillingPaymentMethod paymentMethod,
      Plan plan) {
  }

  record ChangePlan(String subscriptionId, Plan plan) {
  }

}
