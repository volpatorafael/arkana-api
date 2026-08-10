package com.arkana.dto.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BillingPayerRequest(
    @NotBlank @Size(min = 2, max = 120) String name,
    @NotBlank @Pattern(regexp = "[0-9]{11}") String document,
    @NotBlank @Pattern(regexp = "[0-9]{10,11}") String phoneNumber,
    @Valid @NotNull BillingAddressRequest billingAddress) {
}

