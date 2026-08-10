package com.arkana.integration.asaas;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsaasProviderTest {
  private HttpServer server;
  private AtomicReference<Request> request;
  private AtomicReference<Response> response;
  private AsaasProvider provider;

  @BeforeEach
  void setUp() throws IOException {
    request = new AtomicReference<>();
    response = new AtomicReference<>(new Response(
        200,
        "{\"id\":\"chk_123\",\"link\":\"https://sandbox.asaas.com/checkout/chk_123\",\"status\":\"ACTIVE\"}"));
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v3", this::respond);
    server.start();
    provider = new AsaasProvider(
        "test-key",
        "http://localhost:" + server.getAddress().getPort() + "/v3",
        "webhook-token",
        "https://app.getarkana.com",
        new ObjectMapper(),
        HttpClient.newHttpClient());
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void shouldCreateMonthlyRecurringCardCheckout() {
    PaymentProvider.Checkout checkout = provider.createCheckout(new PaymentProvider.CreateCheckout(
        "account-id",
        "checkout-id",
        BillingPaymentMethod.CARD,
        plan("MONTH", 4900)));

    assertThat(checkout.providerId()).isEqualTo("chk_123");
    assertThat(checkout.url()).isEqualTo("https://sandbox.asaas.com/checkout/chk_123");
    assertThat(request.get().method()).isEqualTo("POST");
    assertThat(request.get().path()).isEqualTo("/v3/checkouts");
    assertThat(request.get().accessToken()).isEqualTo("test-key");
    assertThat(request.get().body())
        .contains("\"billingTypes\":[\"CREDIT_CARD\"]")
        .contains("\"chargeTypes\":[\"RECURRENT\"]")
        .contains("\"externalReference\":\"checkout-id\"")
        .contains("\"value\":49.00")
        .contains("\"cycle\":\"MONTHLY\"")
        .contains("\"successUrl\":\"https://app.getarkana.com/app?billing=success\"");
  }

  @Test
  void shouldChangeYearlyPlanAndCancelSubscription() {
    provider.changePlan(new PaymentProvider.ChangePlan("sub_123", plan("YEAR", 39900)));

    assertThat(request.get().method()).isEqualTo("PUT");
    assertThat(request.get().path()).isEqualTo("/v3/subscriptions/sub_123");
    assertThat(request.get().body())
        .contains("\"value\":399.00")
        .contains("\"cycle\":\"YEARLY\"")
        .contains("\"updatePendingPayments\":false");

    provider.cancel("sub_123");
    assertThat(request.get().method()).isEqualTo("DELETE");
    assertThat(request.get().path()).isEqualTo("/v3/subscriptions/sub_123");
  }

  @Test
  void shouldRejectPixAutomaticAndParseAuthenticatedWebhook() {
    assertThatThrownBy(() -> provider.createCheckout(new PaymentProvider.CreateCheckout(
        "account-id",
        "checkout-id",
        BillingPaymentMethod.PIX_AUTOMATIC,
        plan("MONTH", 4900))))
        .hasMessageContaining("Payment method is not available");

    String raw = "{\"id\":\"evt_1\",\"event\":\"PAYMENT_CONFIRMED\","
        + "\"payment\":{\"subscription\":\"sub_1\",\"checkoutSession\":\"chk_1\","
        + "\"externalReference\":\"checkout-id\",\"dueDate\":\"2026-08-10\","
        + "\"ignored\":true},\"newField\":true}";
    PaymentWebhookEvent event = provider.verifyWebhook(
        raw.getBytes(StandardCharsets.UTF_8),
        "webhook-token");

    assertThat(event.id()).isEqualTo("evt_1");
    assertThat(event.eventType()).isEqualTo(BillingProviderEventType.COMPLETED);
    assertThat(event.subscriptionId()).isEqualTo("sub_1");
    assertThat(event.checkoutId()).isEqualTo("checkout-id");
    assertThat(event.providerCheckoutId()).isEqualTo("chk_1");
    assertThatThrownBy(() -> provider.verifyWebhook(
        raw.getBytes(StandardCharsets.UTF_8),
        "wrong-token"))
        .hasMessageContaining("Invalid credentials");
  }

  @Test
  void shouldRejectProviderErrorsAndIncompleteResponses() {
    response.set(new Response(400, "{\"errors\":[{\"description\":\"invalid cycle\"}]}"));
    assertThatThrownBy(() -> provider.createCheckout(new PaymentProvider.CreateCheckout(
        "account-id",
        "checkout-id",
        BillingPaymentMethod.CARD,
        plan("MONTH", 4900))))
        .hasMessageContaining("Asaas rejected the operation");

    response.set(new Response(200, "{\"id\":\"chk_without_link\"}"));
    assertThatThrownBy(() -> provider.createCheckout(new PaymentProvider.CreateCheckout(
        "account-id",
        "checkout-id",
        BillingPaymentMethod.CARD,
        plan("MONTH", 4900))))
        .hasMessageContaining("Asaas returned an incomplete checkout");
  }

  private PaymentProvider.Plan plan(String interval, int amount) {
    return new PaymentProvider.Plan(
        "plan-id",
        "ARKANA_PLAN",
        "Arkana",
        interval,
        amount,
        "BRL",
        null);
  }

  private void respond(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    request.set(new Request(
        exchange.getRequestMethod(),
        exchange.getRequestURI().getPath(),
        exchange.getRequestHeaders().getFirst("access_token"),
        body));
    Response configured = exchange.getRequestURI().getPath().endsWith("/checkouts")
        ? response.get()
        : new Response(200, "{}");
    byte[] responseBody = configured.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(configured.status(), responseBody.length);
    exchange.getResponseBody().write(responseBody);
    exchange.close();
  }

  private record Request(String method, String path, String accessToken, String body) {
  }

  private record Response(int status, String body) {
  }
}
