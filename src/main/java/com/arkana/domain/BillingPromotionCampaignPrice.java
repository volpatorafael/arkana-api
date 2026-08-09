package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@IdClass(BillingPromotionCampaignPriceId.class)
@Table(name = "billing_promotion_campaign_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPromotionCampaignPrice {
  @Id
  @Column(name = "campaign_id", nullable = false)
  private UUID campaignId;
  @Id
  @Column(name = "billing_interval", nullable = false, length = 8)
  private String billingInterval;
  @Column(name = "promotional_plan_price_id", nullable = false)
  private UUID promotionalPlanPriceId;
  @Column(name = "compare_at_plan_price_id", nullable = false)
  private UUID compareAtPlanPriceId;

  public BillingPromotionCampaignPrice(
      UUID campaignId,
      String billingInterval,
      UUID promotionalPlanPriceId,
      UUID compareAtPlanPriceId) {
    this.campaignId = campaignId;
    this.billingInterval = billingInterval;
    this.promotionalPlanPriceId = promotionalPlanPriceId;
    this.compareAtPlanPriceId = compareAtPlanPriceId;
  }

  public UUID getPromotionalPlanPriceId() {
    return promotionalPlanPriceId;
  }
}
