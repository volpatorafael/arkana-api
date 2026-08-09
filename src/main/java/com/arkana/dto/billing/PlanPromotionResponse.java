package com.arkana.dto.billing;

import java.time.OffsetDateTime;

public record PlanPromotionResponse(
    String code,
    String name,
    OffsetDateTime campaignEndsAt,
    OffsetDateTime offerEndsAt,
    String retentionPolicy) {
}
