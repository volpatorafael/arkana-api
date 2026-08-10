package com.arkana.repository;

import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderPlanMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingProviderPlanMappingRepository extends JpaRepository<BillingProviderPlanMapping, UUID> {
  Optional<BillingProviderPlanMapping> findByPlanPriceIdAndProvider(
      UUID planPriceId,
      BillingProvider provider);

  Optional<BillingProviderPlanMapping> findByProviderAndProviderProductId(
      BillingProvider provider,
      String providerProductId);
}
