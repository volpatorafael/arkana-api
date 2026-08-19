package com.arkana.integration.supabase;

import com.arkana.config.SupabaseAdminProperties;
import com.arkana.integration.IdentityAnalyticsProvider.IdentityUser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseIdentityAnalyticsProviderTest {
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void paginatesUsersAndSendsTheSecretOnlyInHeaders() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    AtomicReference<String> apiKey = new AtomicReference<>();
    AtomicReference<String> authorization = new AtomicReference<>();
    server = server(exchange -> {
      requests.incrementAndGet();
      apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      boolean secondPage = exchange.getRequestURI().getQuery().contains("page=2");
      writeJson(exchange, usersJson(secondPage ? 1000 : 0, secondPage ? 1 : 1000), 200);
    });

    SupabaseIdentityAnalyticsProvider provider = provider("server-secret");
    List<IdentityUser> users = provider.users();

    assertThat(users).hasSize(1001);
    assertThat(users.getFirst().emailConfirmedAt()).isNotNull();
    assertThat(users.getFirst().anonymous()).isFalse();
    assertThat(requests).hasValue(2);
    assertThat(apiKey).hasValue("server-secret");
    assertThat(authorization).hasValue("Bearer server-secret");
  }

  @Test
  void mapsProviderFailureToServiceUnavailable() throws Exception {
    server = server(exchange -> writeJson(exchange, "{}", 500));

    assertThatThrownBy(() -> provider("server-secret").users())
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value()));
  }

  @Test
  void failsClosedWhenTheSecretIsMissing() {
    SupabaseIdentityAnalyticsProvider provider = new SupabaseIdentityAnalyticsProvider(
        new SupabaseAdminProperties("https://project.supabase.co", ""));

    assertThatThrownBy(provider::users)
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode().value())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value()));
  }

  private SupabaseIdentityAnalyticsProvider provider(String secret) {
    return new SupabaseIdentityAnalyticsProvider(new SupabaseAdminProperties(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/auth/v1",
        secret));
  }

  private HttpServer server(ExchangeHandler handler) throws IOException {
    HttpServer value = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    value.createContext("/auth/v1/admin/users", exchange -> handler.handle(exchange));
    value.start();
    return value;
  }

  private String usersJson(int start, int count) {
    List<String> users = new ArrayList<>();
    for (int index = start; index < start + count; index++) {
      UUID id = new UUID(0, index + 1L);
      users.add("""
          {"id":"%s","created_at":"2026-08-19T10:00:00Z",\
          "email_confirmed_at":"2026-08-19T10:01:00Z",\
          "last_sign_in_at":"2026-08-19T10:02:00Z","is_anonymous":false}
          """.formatted(id).replace("\n", ""));
    }
    return "{\"users\":[" + String.join(",", users) + "]}";
  }

  private void writeJson(HttpExchange exchange, String body, int status) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @FunctionalInterface
  private interface ExchangeHandler {
    void handle(HttpExchange exchange) throws IOException;
  }
}
