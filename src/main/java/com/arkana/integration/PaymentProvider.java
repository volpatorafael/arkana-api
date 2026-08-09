package com.arkana.integration;

import java.time.OffsetDateTime;
import java.util.Map;

public interface PaymentProvider {
  Checkout createCheckout(String accountId, String checkoutId, String productId, String paymentMethod);

  void cancel(String subscriptionId);

  void changePlan(String subscriptionId, String productId);

  Map<String, Object> verifyWebhook(byte[] rawBody, String signature);

  record Checkout(String providerId, String url, OffsetDateTime expiresAt) {
  }
}
