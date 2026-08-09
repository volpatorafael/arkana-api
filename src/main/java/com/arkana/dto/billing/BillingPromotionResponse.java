package com.arkana.dto.billing;

import java.time.OffsetDateTime;

public record BillingPromotionResponse(
    String code,
    String name,
    String status,
    OffsetDateTime campaignEndsAt,
    OffsetDateTime firstCheckoutEndsAt,
    OffsetDateTime lockedAt) {
}
