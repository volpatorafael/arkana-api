package com.arkana.integration.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasWebhookRequest(
    String id,
    String event,
    Payment payment,
    Subscription subscription,
    Checkout checkout) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Payment(
      String id,
      String subscription,
      String checkoutSession,
      String externalReference,
      String dueDate,
      String billingType) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Subscription(
      String id,
      String externalReference,
      String nextDueDate,
      String cycle,
      String billingType) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Checkout(String id, String externalReference, String status) {
  }
}
