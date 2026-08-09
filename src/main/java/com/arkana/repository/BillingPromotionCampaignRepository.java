package com.arkana.repository;

import com.arkana.domain.BillingPromotionCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BillingPromotionCampaignRepository extends JpaRepository<BillingPromotionCampaign, UUID> {
  List<BillingPromotionCampaign> findAllByStatusAndStartsAtLessThanEqualAndEndsAtGreaterThan(
      String status,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt);
}
