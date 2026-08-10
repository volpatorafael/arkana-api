package com.arkana.repository;

import com.arkana.domain.BillingProviderCharge;
import com.arkana.domain.BillingProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface BillingProviderChargeRepository extends JpaRepository<BillingProviderCharge, UUID> {
  Optional<BillingProviderCharge> findByProviderSubscriptionIdAndDueAt(
      UUID providerSubscriptionId,
      OffsetDateTime dueAt);

  Optional<BillingProviderCharge> findByProviderAndProviderChargeId(
      BillingProvider provider,
      String providerChargeId);

  List<BillingProviderCharge> findAllByProviderSubscriptionIdAndStatus(
      UUID providerSubscriptionId,
      com.arkana.domain.BillingProviderChargeStatus status);
}
