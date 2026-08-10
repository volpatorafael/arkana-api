package com.arkana.integration.asaas;

import java.math.BigDecimal;

public record AsaasChangeSubscriptionRequest(
    BigDecimal value,
    String cycle,
    String description,
    boolean updatePendingPayments) {
}
