package com.arkana.dto.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BillingCheckoutResponse(
    UUID id,
    BillingCheckoutActionResponse action,
    OffsetDateTime expiresAt) {
}
