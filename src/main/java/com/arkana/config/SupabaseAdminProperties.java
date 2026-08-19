package com.arkana.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arkana.supabase")
public record SupabaseAdminProperties(String url, String secretKey) {
  public boolean configured() {
    return url != null && !url.isBlank() && secretKey != null && !secretKey.isBlank();
  }
}
