package com.arkana.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBillingCheckoutRequest(@NotNull UUID planPriceId, @NotBlank String paymentMethod) {
}
