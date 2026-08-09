package com.arkana.repository;

import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BillingProviderEventRepository extends JpaRepository<BillingProviderEvent, UUID> {
  boolean existsByProviderAndProviderEventId(BillingProvider provider, String eventId);

  long countByProviderEventId(String eventId);
}
