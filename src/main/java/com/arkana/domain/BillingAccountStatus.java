package com.arkana.domain;

public enum BillingAccountStatus {
  PENDING_PAYMENT,
  TRIALING,
  ACTIVE,
  CANCEL_AT_PERIOD_END,
  EXPIRED,
  PAST_DUE,
  CANCELED
}
