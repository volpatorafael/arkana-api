package com.arkana.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arkana.supabase")
public record SupabaseAdminProperties(String url, String secretKey) {
  public boolean configured() {
    return url != null && !url.isBlank() && secretKey != null && !secretKey.isBlank();
  }

  public String normalizedUrl() {
    if (url == null || url.isBlank()) {
      return "";
    }
    String n = url.trim();
    while (n.endsWith("/")) {
      n = n.substring(0, n.length() - 1);
    }
    if (n.endsWith("/auth/v1")) {
      n = n.substring(0, n.length() - "/auth/v1".length());
    }
    return n;
  }
}
