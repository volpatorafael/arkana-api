package com.arkana.dto.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveClientRequest(
    @NotBlank(message = "name is required.")
    @Size(max = 160, message = "name must contain at most 160 characters.")
    String name,
    @Email(message = "email must be valid.")
    @Size(max = 320, message = "email must contain at most 320 characters.")
    String email,
    @Size(max = 40, message = "phone must contain at most 40 characters.")
    String phone,
    @Size(max = 10000, message = "notes must contain at most 10000 characters.")
    String notes) {
}
