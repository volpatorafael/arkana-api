package com.arkana.mapper;

import com.arkana.domain.BillingAccount;
import com.arkana.dto.billing.BillingOverview;
import com.arkana.dto.billing.BillingPlanSummary;
import com.arkana.dto.billing.BillingPromotionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface BillingAccountMapper {
  @Mapping(target = "status", source = "status")
  @Mapping(target = "accessStatus", source = "accessStatus")
  @Mapping(target = "trialStartedAt", source = "account.trialStartedAt")
  @Mapping(target = "trialEndsAt", source = "account.trialEndsAt")
  @Mapping(target = "currentPeriodStart", source = "account.currentPeriodStart")
  @Mapping(target = "currentPeriodEnd", source = "account.currentPeriodEnd")
  @Mapping(target = "cancelAtPeriodEnd", source = "account.cancelAtPeriodEnd")
  @Mapping(target = "currentPlan", source = "currentPlan")
  @Mapping(target = "pendingPlan", source = "pendingPlan")
  @Mapping(target = "overrideEndsAt", source = "overrideEndsAt")
  @Mapping(target = "availablePaymentMethods", source = "availablePaymentMethods")
  @Mapping(target = "canCheckout", source = "canCheckout")
  @Mapping(target = "canCancel", source = "canCancel")
  @Mapping(target = "canChangePlan", source = "canChangePlan")
  @Mapping(target = "promotion", source = "promotion")
  BillingOverview toOverview(
      BillingAccount account,
      String status,
      String accessStatus,
      BillingPlanSummary currentPlan,
      BillingPlanSummary pendingPlan,
      OffsetDateTime overrideEndsAt,
      List<String> availablePaymentMethods,
      boolean canCheckout,
      boolean canCancel,
      boolean canChangePlan,
      BillingPromotionResponse promotion);

  @Mapping(target = "status", constant = "PENDING_PAYMENT")
  @Mapping(target = "accessStatus", constant = "BLOCKED")
  @Mapping(target = "trialStartedAt", expression = "java(null)")
  @Mapping(target = "trialEndsAt", expression = "java(null)")
  @Mapping(target = "currentPeriodStart", expression = "java(null)")
  @Mapping(target = "currentPeriodEnd", expression = "java(null)")
  @Mapping(target = "cancelAtPeriodEnd", constant = "false")
  @Mapping(target = "currentPlan", expression = "java(null)")
  @Mapping(target = "pendingPlan", expression = "java(null)")
  @Mapping(target = "overrideEndsAt", expression = "java(null)")
  @Mapping(target = "availablePaymentMethods", source = "availablePaymentMethods")
  @Mapping(target = "canCheckout", constant = "false")
  @Mapping(target = "canCancel", constant = "false")
  @Mapping(target = "canChangePlan", constant = "false")
  @Mapping(target = "promotion", expression = "java(null)")
  BillingOverview toEmptyOverview(BillingOverviewMappingSource source);
}
