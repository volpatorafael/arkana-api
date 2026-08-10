package com.arkana.mapper;

import com.arkana.domain.BillingCheckout;
import com.arkana.dto.billing.BillingCheckoutResponse;
import com.arkana.dto.billing.BillingCheckoutActionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.OffsetDateTime;
import java.util.UUID;

@Mapper
public interface BillingCheckoutMapper {
  @Mapping(target = "action", source = ".")
  BillingCheckoutResponse toResponse(BillingCheckout checkout);

  @Mapping(target = "type", source = "actionType")
  @Mapping(target = "url", source = "checkoutUrl")
  @Mapping(target = "copyPasteCode", source = "actionCode")
  @Mapping(target = "qrCodeImage", source = "actionImage")
  BillingCheckoutActionResponse toAction(BillingCheckout checkout);
}
