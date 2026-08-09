package com.arkana.service;

import com.arkana.domain.BillingAccount;
import com.arkana.repository.BillingAccessOverrideRepository;
import com.arkana.repository.BillingAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BillingAccessEvaluator {
  private final BillingAccountRepository accounts;
  private final BillingAccessOverrideRepository overrides;

  @Transactional(readOnly = true)
  public boolean hasActiveAccess(UUID ownerId) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Optional<BillingAccount> account = accounts.findByOwnerId(ownerId);
    if (account.isEmpty()) {
      return false;
    }
    BillingAccount value = account.get();
    boolean trial = "TRIALING".equals(value.getStatus())
        && value.getTrialEndsAt() != null
        && value.getTrialEndsAt().isAfter(now);
    boolean subscription = ("ACTIVE".equals(value.getStatus())
        || "CANCEL_AT_PERIOD_END".equals(value.getStatus()))
        && value.getCurrentPeriodEnd() != null
        && value.getCurrentPeriodEnd().isAfter(now);
    boolean legacyOverride = value.getOverrideEndsAt() != null
        && value.getOverrideEndsAt().isAfter(now);
    boolean activeOverride = overrides
        .findFirstByBillingAccountIdAndStartsAtLessThanEqualAndEndsAtGreaterThanAndRevokedAtIsNullOrderByEndsAtDesc(
            value.getId(),
            now,
            now)
        .isPresent();
    return trial || subscription || legacyOverride || activeOverride;
  }
}
