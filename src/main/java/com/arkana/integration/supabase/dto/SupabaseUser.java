package com.arkana.integration.supabase.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SupabaseUser(
    UUID id,
    String email,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @JsonProperty("last_sign_in_at") OffsetDateTime lastSignInAt
) {}
