package com.arkana.integration;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface IdentityAnalyticsProvider {
  List<IdentityUser> users();

  record IdentityUser(
      UUID id,
      OffsetDateTime createdAt,
      OffsetDateTime emailConfirmedAt,
      OffsetDateTime lastSignInAt,
      boolean anonymous) {
  }
}

