package com.arkana.integration.asaas;

import java.math.BigDecimal;
import java.util.List;

public record AsaasCreateCheckoutRequest(
    List<String> billingTypes,
    List<String> chargeTypes,
    int minutesToExpire,
    String externalReference,
    Callback callback,
    List<Item> items,
    Subscription subscription) {

  public record Callback(String successUrl, String cancelUrl, String expiredUrl) {
  }

  public record Item(String name, String description, int quantity, BigDecimal value) {
  }

  public record Subscription(String cycle, String nextDueDate) {
  }
}
