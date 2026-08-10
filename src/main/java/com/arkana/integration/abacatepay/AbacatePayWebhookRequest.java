package com.arkana.integration.abacatepay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AbacatePayWebhookRequest(
    String id,
    String event,
    Data data) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Data(
      Subscription subscription,
      Checkout checkout) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Subscription(
      String id,
      String productId,
      Product product,
      OffsetDateTime currentPeriodStart,
      OffsetDateTime currentPeriodEnd,
      OffsetDateTime nextBillingAt,
      OffsetDateTime trialEndsAt) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Product(String id) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Checkout(
      String externalId,
      Metadata metadata) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Metadata(String checkoutId) {
  }
}
