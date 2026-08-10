package com.arkana.integration;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.integration.dto.PaymentWebhookEvent;

import java.time.OffsetDateTime;

public interface PaymentProvider {
  Checkout createCheckout(
      String accountId,
      String checkoutId,
      String productId,
      BillingPaymentMethod paymentMethod);

  void cancel(String subscriptionId);

  void changePlan(String subscriptionId, String productId);

  PaymentWebhookEvent verifyWebhook(byte[] rawBody, String signature);

  record Checkout(String providerId, String url, OffsetDateTime expiresAt) {
  }

}
