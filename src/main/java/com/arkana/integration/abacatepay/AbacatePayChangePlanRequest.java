package com.arkana.integration.abacatepay;

public record AbacatePayChangePlanRequest(
    String id,
    String productId,
    int quantity) {
}
