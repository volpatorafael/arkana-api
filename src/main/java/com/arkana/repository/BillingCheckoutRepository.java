package com.arkana.repository;

import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingCheckoutStatus;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingCheckoutRepository extends JpaRepository<BillingCheckout, UUID> {
  Optional<BillingCheckout> findByBillingAccountIdAndIdempotencyKey(UUID accountId, UUID idempotencyKey);

  Optional<BillingCheckout> findByProviderAndProviderCheckoutId(
      BillingProvider provider,
      String providerCheckoutId);

  Optional<BillingCheckout> findFirstByBillingAccountIdAndProviderAndStatusInOrderByCreatedAtDesc(
      UUID accountId,
      BillingProvider provider,
      Collection<BillingCheckoutStatus> statuses);

}
