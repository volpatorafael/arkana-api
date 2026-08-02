package com.arkana.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arkana")
public record ArkanaProperties(Cors cors) {
    public record Cors(List<String> allowedOrigins) {
    }
}
