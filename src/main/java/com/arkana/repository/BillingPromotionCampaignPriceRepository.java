package com.arkana.repository;

import com.arkana.domain.BillingPromotionCampaignPrice;
import com.arkana.domain.BillingPromotionCampaignPriceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BillingPromotionCampaignPriceRepository
    extends JpaRepository<BillingPromotionCampaignPrice, BillingPromotionCampaignPriceId> {
  List<BillingPromotionCampaignPrice> findAllByCampaignIdIn(Collection<UUID> campaignIds);

  List<BillingPromotionCampaignPrice> findAllByPromotionalPlanPriceId(UUID planPriceId);
}
