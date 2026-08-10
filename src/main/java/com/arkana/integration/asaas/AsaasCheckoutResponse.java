package com.arkana.integration.asaas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AsaasCheckoutResponse(String id, String link, String status) {
}
