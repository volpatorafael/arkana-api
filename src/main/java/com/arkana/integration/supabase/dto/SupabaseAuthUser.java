package com.arkana.integration.supabase.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SupabaseAuthUser(
    UUID id,
    String email,
    OffsetDateTime createdAt,
    OffsetDateTime lastSignInAt
) {}
