package com.arkana.integration.abacatepay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AbacatePayApiResponse<T>(T data, Object error) {
}
