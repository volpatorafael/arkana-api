package com.arkana.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "arkana")
public record ArkanaProperties(Cors cors) {
  public record Cors(List<String> allowedOrigins) {
  }
}
