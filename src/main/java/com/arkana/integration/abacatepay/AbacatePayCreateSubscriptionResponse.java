package com.arkana.integration.abacatepay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AbacatePayCreateSubscriptionResponse(
    String id,
    String url,
    OffsetDateTime expiresAt) {
}
