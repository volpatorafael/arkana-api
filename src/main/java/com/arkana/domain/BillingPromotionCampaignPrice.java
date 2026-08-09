package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@IdClass(BillingPromotionCampaignPriceId.class)
@Table(name = "billing_promotion_campaign_prices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

}
