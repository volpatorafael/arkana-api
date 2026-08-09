package com.arkana.mapper;

import com.arkana.domain.BillingPlanPrice;
import com.arkana.dto.billing.BillingPlanSummary;
import com.arkana.dto.billing.PlanPromotionResponse;
import com.arkana.dto.billing.SubscriptionPlanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface BillingPlanPriceMapper {
  @Mapping(target = "interval", source = "billingInterval")
  BillingPlanSummary toSummary(BillingPlanPrice plan);

  @Mapping(target = "id", source = "plan.id")
  @Mapping(target = "code", source = "plan.code")
  @Mapping(target = "name", source = "plan.name")
  @Mapping(target = "interval", source = "plan.billingInterval")
  @Mapping(target = "amount", source = "plan.amount")
  @Mapping(target = "compareAtAmount", source = "compareAtAmount")
  @Mapping(target = "currency", source = "plan.currency")
  @Mapping(target = "trialDays", source = "plan.trialDays")
  @Mapping(target = "annualSavingsPercent", source = "annualSavingsPercent")
  @Mapping(target = "availablePaymentMethods", source = "plan.availablePaymentMethods")
  @Mapping(target = "promotion", source = "promotion")
  SubscriptionPlanResponse toResponse(
      BillingPlanPrice plan,
      Integer compareAtAmount,
      Double annualSavingsPercent,
      PlanPromotionResponse promotion);
}
