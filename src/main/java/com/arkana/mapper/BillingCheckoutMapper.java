package com.arkana.mapper;

import com.arkana.domain.BillingCheckout;
import com.arkana.dto.billing.BillingCheckoutResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.OffsetDateTime;
import java.util.UUID;

@Mapper
public interface BillingCheckoutMapper {
  @Mapping(target = "url", source = "checkoutUrl")
  BillingCheckoutResponse toResponse(BillingCheckout checkout);

  BillingCheckoutResponse toResponse(
      UUID id,
      String url,
      OffsetDateTime expiresAt);
}
