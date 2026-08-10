package com.arkana.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum BillingProviderEventType {
  TRIAL_STARTED("subscription.trial_started"),
  COMPLETED("subscription.completed"),
  RENEWED("subscription.renewed"),
  PAYMENT_FAILED("subscription.payment_failed"),
  CANCELED("subscription.cancelled"),
  PLAN_CHANGED("subscription.plan_changed"),
  SUBSCRIPTION_LINKED("subscription.linked"),
  IGNORED("ignored");

  private final String eventValue;

  public static Optional<BillingProviderEventType> fromEventValue(String eventValue) {
    return Arrays.stream(values())
        .filter(eventType -> eventType.eventValue.equals(eventValue))
        .findFirst();
  }
}
