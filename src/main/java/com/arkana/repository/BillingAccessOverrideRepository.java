package com.arkana.repository;

import com.arkana.domain.BillingAccessOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BillingAccessOverrideRepository extends JpaRepository<BillingAccessOverride, UUID> {
  Optional<BillingAccessOverride>
  findFirstByBillingAccountIdAndStartsAtLessThanEqualAndEndsAtGreaterThanAndRevokedAtIsNullOrderByEndsAtDesc(
      UUID accountId,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt);
}
