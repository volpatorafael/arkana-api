package com.arkana.dto.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BillingAddressRequest(
    @NotBlank @Size(max = 160) String street,
    @NotBlank @Size(max = 20) String number,
    @NotBlank @Size(max = 80) String neighborhood,
    @NotBlank @Pattern(regexp = "[0-9]{8}") String zipCode,
    @NotBlank @Size(max = 80) String city,
    @NotBlank @Pattern(regexp = "[A-Z]{2}") String state,
    @Size(max = 80) String complement) {
}

