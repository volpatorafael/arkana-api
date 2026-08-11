package com.arkana.integration.efi;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(OutputCaptureExtension.class)
class EfiProviderTest {
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-10T12:00:00Z"), ZoneOffset.UTC);
  private HttpServer server;
  private EfiProvider provider;
  private AtomicInteger oauthRequests;
  private AtomicReference<String> subscriptionBody;
  private boolean rejectSubscription;

  @BeforeEach
  void setUp() throws IOException {
    oauthRequests = new AtomicInteger();
    subscriptionBody = new AtomicReference<>();
    rejectSubscription = false;
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", this::respond);
    server.start();
    String baseUrl = "http://localhost:" + server.getAddress().getPort();
    provider = new EfiProvider(
        "client-id",
        "client-secret",
        baseUrl,
        baseUrl,
        "pix-key",
        "",
        "",
        "charges-secret",
        "pix-secret",
        "https://api.getarkana.com",
        new ObjectMapper(),
        CLOCK);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void shouldCreateCardSubscriptionWithTrialAndReuseOAuthToken() {
    PaymentProvider.Checkout checkout = provider.createCheckout(command(BillingPaymentMethod.CARD));

    assertThat(checkout.actionType()).isEqualTo(PaymentProvider.ActionType.PENDING_CONFIRMATION);
    assertThat(checkout.subscriptionId()).isEqualTo("subscription-123");
    assertThat(subscriptionBody.get())
        .contains("\"payment_token\":\"browser-token\"")
        .contains("\"trial_days\":14")
        .contains("\"custom_id\":\"checkout-id\"")
        .contains("/v1/webhook/payment/efi/charges?webhookSecret=charges-secret");

    PaymentWebhookEvent event = provider.verifyWebhook(
        "notification-token".getBytes(StandardCharsets.UTF_8),
        "charges-secret");
    assertThat(oauthRequests).hasValue(1);
    assertThat(event.id()).isEqualTo("charges:history-2");
    assertThat(event.eventType()).isEqualTo(BillingProviderEventType.COMPLETED);
    assertThat(event.subscriptionId()).isEqualTo("subscription-123");
    assertThat(event.rawPayload()).doesNotContain("cpf", "phone", "payment_token");
  }

  @Test
  void shouldParsePixEventsWithDeterministicIdsAndConstantTimeSecretCheck() {
    String payload = "{\"recs\":[{\"idRec\":\"rec-123\",\"status\":\"APROVADA\","
        + "\"devedor\":{\"cpf\":\"52998224725\"},"
        + "\"atualizacao\":[{\"data\":\"2026-08-10T12:01:00Z\"}]}]}";

    PaymentWebhookEvent event = provider.verifyWebhook(
        payload.getBytes(StandardCharsets.UTF_8),
        "pix-secret");
    assertThat(event.id()).isEqualTo("pix-rec:rec-123:APROVADA:2026-08-10T12:01:00Z");
    assertThat(event.eventType()).isEqualTo(BillingProviderEventType.SUBSCRIPTION_LINKED);
    assertThat(event.paymentMethod()).isEqualTo(BillingPaymentMethod.PIX_AUTOMATIC);
    assertThat(event.rawPayload()).doesNotContain("52998224725", "devedor");

    assertThatThrownBy(() -> provider.verifyWebhook(
        payload.getBytes(StandardCharsets.UTF_8),
        "wrong-secret"))
        .hasMessageContaining("Invalid credentials");
  }

  @Test
  void shouldExposeUsefulSafeMessageAndLogCompleteProviderResponse(CapturedOutput output) {
    rejectSubscription = true;

    Throwable failure = catchThrowable(
        () -> provider.createCheckout(command(BillingPaymentMethod.CARD)));

    assertThat(failure)
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Efí could not process the payment right now");
    assertThat(failure.getMessage())
        .doesNotContain("provider-payment-token", "52998224725");

    assertThat(output.getAll())
        .contains("status=500")
        .contains("responseBody={\"code\":3500034,\"error\":\"validation_error\","
            + "\"error_description\":{\"property\":\"/payment/credit_card/trial_days\","
            + "\"message\":\"Propriedade desconhecida (não está no schema).\"}}")
        .doesNotContain("browser-token", "52998224725");
  }

  private PaymentProvider.CreateCheckout command(BillingPaymentMethod method) {
    return new PaymentProvider.CreateCheckout(
        "account-id",
        "checkout-id",
        "reader@example.com",
        method,
        new PaymentProvider.Plan(
            "plan-id", "ARKANA_MONTHLY", "Arkana Mensal", "MONTH", 4900, "BRL", "42"),
        OffsetDateTime.parse("2026-08-24T12:00:00Z"),
        new PaymentProvider.Payer(
            "Maria da Silva",
            "52998224725",
            "11999999999",
            new PaymentProvider.Address(
                "Rua Um", "10", "Centro", "01001000", "Sao Paulo", "SP", null)),
        "browser-token");
  }

  private void respond(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String response;
    int status = 200;
    if (path.equals("/v1/authorize")) {
      oauthRequests.incrementAndGet();
      response = "{\"access_token\":\"access-token\",\"expires_in\":300}";
    } else if (path.contains("/subscription/one-step")) {
      subscriptionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      if (rejectSubscription) {
        status = 500;
        response = "{\"code\":3500034,\"error\":\"validation_error\","
            + "\"error_description\":{"
            + "\"property\":\"/payment/credit_card/trial_days\","
            + "\"message\":\"Propriedade desconhecida (não está no schema).\"}}";
      } else {
        response = "{\"data\":{\"subscription_id\":\"subscription-123\","
            + "\"charge\":{\"id\":\"charge-123\"}}}";
      }
    } else if (path.equals("/v1/notification/notification-token")) {
      response = "{\"data\":[{\"id\":\"history-1\",\"type\":\"charge\","
          + "\"status\":{\"current\":\"waiting\"}},{\"id\":\"history-2\","
          + "\"type\":\"charge\",\"status\":{\"current\":\"paid\"},"
          + "\"identifiers\":{\"subscription_id\":\"subscription-123\","
          + "\"charge_id\":\"charge-123\"},\"custom_id\":\"checkout-id\"}]}";
    } else {
      exchange.sendResponseHeaders(404, -1);
      exchange.close();
      return;
    }
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
