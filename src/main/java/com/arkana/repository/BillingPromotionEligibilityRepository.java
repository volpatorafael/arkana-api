package com.arkana.repository;

import com.arkana.domain.BillingPromotionEligibility;
import com.arkana.domain.BillingPromotionEligibilityStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BillingPromotionEligibilityRepository extends JpaRepository<BillingPromotionEligibility, UUID> {
  List<BillingPromotionEligibility> findAllByBillingAccountIdOrderByGrantedAtDesc(UUID accountId);

  List<BillingPromotionEligibility> findAllByBillingAccountIdAndStatusIn(
      UUID accountId,
      Collection<BillingPromotionEligibilityStatus> statuses);

  List<BillingPromotionEligibility> findAllByBillingAccountIdAndCampaignIdInAndStatus(
      UUID accountId,
      Collection<UUID> campaignIds,
      BillingPromotionEligibilityStatus status);
}
