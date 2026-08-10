package com.arkana.integration.abacatepay;

import java.util.List;

public record AbacatePayCreateSubscriptionRequest(
    String completionUrl,
    String returnUrl,
    String externalId,
    List<Item> items,
    List<String> methods,
    Metadata metadata) {

  public record Item(String id, int quantity) {
  }

  public record Metadata(String billingAccountId, String checkoutId) {
  }
}
