package com.arkana.integration.supabase;

import com.arkana.config.SupabaseAdminProperties;
import com.arkana.integration.IdentityAnalyticsProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class SupabaseIdentityAnalyticsProvider implements IdentityAnalyticsProvider {
  private static final int PAGE_SIZE = 1000;
  private static final int MAXIMUM_PAGES = 10_000;

  private final SupabaseAdminProperties properties;
  private final RestClient restClient;

  public SupabaseIdentityAnalyticsProvider(SupabaseAdminProperties properties) {
    this.properties = properties;
    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
    requestFactory.setReadTimeout(Duration.ofSeconds(4));
    this.restClient = RestClient.builder()
        .requestFactory(requestFactory)
        .build();
  }

  @Override
  @Cacheable(cacheNames = "supabase-identity-users", cacheManager = "identityAnalyticsCacheManager")
  public List<IdentityUser> users() {
    if (!properties.configured()) {
      throw unavailable();
    }
    try {
      List<IdentityUser> users = new ArrayList<>();
      for (int page = 1; page <= MAXIMUM_PAGES; page++) {
        SupabaseUsersResponse response = restClient.get()
            .uri(baseUrl() + "/auth/v1/admin/users?page={page}&per_page={perPage}", page, PAGE_SIZE)
            .header("apikey", properties.secretKey())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
            .retrieve()
            .body(SupabaseUsersResponse.class);
        if (response == null || response.users() == null) {
          throw unavailable();
        }
        if (response.users().stream().anyMatch(user -> user.id() == null || user.createdAt() == null)) {
          throw unavailable();
        }
        users.addAll(response.users().stream().map(SupabaseUser::toIdentityUser).toList());
        if (response.users().size() < PAGE_SIZE) {
          return List.copyOf(users);
        }
      }
      throw unavailable();
    } catch (RestClientException exception) {
      throw unavailable();
    }
  }

  private ResponseStatusException unavailable() {
    return new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Identity analytics is temporarily unavailable.");
  }

  private String baseUrl() {
    String url = properties.url();
    String normalized = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    return normalized.endsWith("/auth/v1")
        ? normalized.substring(0, normalized.length() - "/auth/v1".length())
        : normalized;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SupabaseUsersResponse(List<SupabaseUser> users) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SupabaseUser(
      UUID id,
      @JsonProperty("created_at") OffsetDateTime createdAt,
      @JsonProperty("email_confirmed_at") OffsetDateTime emailConfirmedAt,
      @JsonProperty("last_sign_in_at") OffsetDateTime lastSignInAt,
      @JsonProperty("is_anonymous") boolean anonymous) {

    IdentityUser toIdentityUser() {
      return new IdentityUser(id, createdAt, emailConfirmedAt, lastSignInAt, anonymous);
    }
  }
}
