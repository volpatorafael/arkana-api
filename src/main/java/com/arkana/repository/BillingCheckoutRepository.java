package com.arkana.repository;

import com.arkana.domain.BillingCheckout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingCheckoutRepository extends JpaRepository<BillingCheckout, UUID> {
  Optional<BillingCheckout> findByBillingAccountIdAndIdempotencyKey(UUID accountId, UUID idempotencyKey);

}
