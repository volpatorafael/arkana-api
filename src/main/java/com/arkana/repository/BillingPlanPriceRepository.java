package com.arkana.repository;

import com.arkana.domain.BillingPlanPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BillingPlanPriceRepository extends JpaRepository<BillingPlanPrice, UUID> {
  List<BillingPlanPrice> findAllByActiveTrueAndDefaultPlanTrueOrderByAmountAsc();
}
