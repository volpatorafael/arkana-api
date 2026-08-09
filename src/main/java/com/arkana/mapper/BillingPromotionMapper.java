package com.arkana.mapper;

import com.arkana.domain.BillingPromotionCampaign;
import com.arkana.domain.BillingPromotionEligibility;
import com.arkana.dto.billing.BillingPromotionResponse;
import com.arkana.dto.billing.PlanPromotionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.OffsetDateTime;

@Mapper
public interface BillingPromotionMapper {
  @Mapping(target = "code", source = "campaign.code")
  @Mapping(target = "name", source = "campaign.name")
  @Mapping(target = "status", source = "status")
  @Mapping(target = "campaignEndsAt", source = "campaign.endsAt")
  @Mapping(target = "firstCheckoutEndsAt", source = "eligibility.firstCheckoutEndsAt")
  @Mapping(target = "lockedAt", source = "eligibility.lockedAt")
  BillingPromotionResponse toResponse(
      BillingPromotionCampaign campaign,
      BillingPromotionEligibility eligibility,
      String status);

  @Mapping(target = "code", source = "campaign.code")
  @Mapping(target = "name", source = "campaign.name")
  @Mapping(target = "campaignEndsAt", source = "campaign.endsAt")
  @Mapping(target = "offerEndsAt", source = "offerEndsAt")
  @Mapping(target = "retentionPolicy", source = "campaign.retentionPolicy")
  PlanPromotionResponse toOfferResponse(
      BillingPromotionCampaign campaign,
      OffsetDateTime offerEndsAt);
}
