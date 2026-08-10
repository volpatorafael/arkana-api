package com.arkana.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateBillingCheckoutRequest(
    @NotNull UUID planPriceId,
    @NotBlank String paymentMethod,
    @Size(max = 500) String paymentToken,
    @Valid @NotNull BillingPayerRequest payer) {
}
