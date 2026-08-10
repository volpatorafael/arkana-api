package com.arkana.integration.asaas;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class AsaasProvider implements PaymentProvider {
  private final String apiKey;
  private final String apiUrl;
  private final String webhookToken;
  private final String appUrl;
  private final HttpClient http;
  private final ObjectMapper json;

  @Autowired
  public AsaasProvider(
      @Value("${arkana.asaas.api-key:}") String apiKey,
      @Value("${arkana.asaas.api-url:https://api-sandbox.asaas.com/v3}") String apiUrl,
      @Value("${arkana.asaas.webhook-token:}") String webhookToken,
      @Value("${arkana.app-url:http://localhost:5173}") String appUrl,
      ObjectMapper json) {
    this(apiKey, apiUrl, webhookToken, appUrl, json, HttpClient.newHttpClient());
  }

  AsaasProvider(
      String apiKey,
      String apiUrl,
      String webhookToken,
      String appUrl,
      ObjectMapper json,
      HttpClient http) {
    this.apiKey = apiKey;
    this.apiUrl = apiUrl.replaceAll("/+$", "");
    this.webhookToken = webhookToken;
    this.appUrl = appUrl.replaceAll("/+$", "");
    this.json = json;
    this.http = http;
  }

  @Override
  public BillingProvider provider() {
    return BillingProvider.ASAAS;
  }

  @Override
  public Set<BillingPaymentMethod> supportedPaymentMethods() {
    return Set.of(BillingPaymentMethod.CARD);
  }

  @Override
  public boolean requiresPlanMapping() {
    return false;
  }

  @Override
  public Checkout createCheckout(CreateCheckout command) {
    if (command.paymentMethod() != BillingPaymentMethod.CARD) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is not available.");
    }
    String successUrl = appUrl + "/app?billing=success";
    String returnUrl = appUrl + "/app?billing=return";
    AsaasCreateCheckoutRequest body = new AsaasCreateCheckoutRequest(
        List.of("CREDIT_CARD"),
        List.of("RECURRENT"),
        30,
        command.checkoutId(),
        new AsaasCreateCheckoutRequest.Callback(successUrl, returnUrl, returnUrl),
        List.of(new AsaasCreateCheckoutRequest.Item(
            command.plan().name(),
            command.plan().name(),
            1,
            amount(command.plan().amount()))),
        new AsaasCreateCheckoutRequest.Subscription(
            cycle(command.plan().interval()),
            LocalDate.now(ZoneOffset.UTC).toString()));
    AsaasCheckoutResponse response = request("POST", "/checkouts", body, AsaasCheckoutResponse.class);
    if (response == null || response.id() == null || response.link() == null) {
      log.error(
          "Asaas returned an incomplete checkout. providerIdPresent={}, linkPresent={}",
          response != null && response.id() != null,
          response != null && response.link() != null);
      throw unavailable("Asaas returned an incomplete checkout.");
    }
    return new Checkout(
        response.id(),
        response.link(),
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30));
  }

  @Override
  public void cancel(String subscriptionId) {
    request("DELETE", "/subscriptions/" + encode(subscriptionId), null, Object.class);
  }

  @Override
  public void changePlan(ChangePlan command) {
    AsaasChangeSubscriptionRequest body = new AsaasChangeSubscriptionRequest(
        amount(command.plan().amount()),
        cycle(command.plan().interval()),
        command.plan().name(),
        false);
    request(
        "PUT",
        "/subscriptions/" + encode(command.subscriptionId()),
        body,
        Object.class);
  }

  @Override
  public PaymentWebhookEvent verifyWebhook(byte[] raw, String suppliedToken) {
    if (webhookToken.isBlank()
        || !java.security.MessageDigest.isEqual(
        webhookToken.getBytes(StandardCharsets.UTF_8),
        suppliedToken.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }
    try {
      AsaasWebhookRequest webhook = json.readValue(raw, AsaasWebhookRequest.class);
      if (webhook.id() == null || webhook.id().isBlank() || webhook.event() == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event.");
      }
      BillingProviderEventType eventType = eventType(webhook.event());
      AsaasWebhookRequest.Payment payment = webhook.payment();
      AsaasWebhookRequest.Subscription subscription = webhook.subscription();
      AsaasWebhookRequest.Checkout checkout = webhook.checkout();
      String subscriptionId = payment != null && payment.subscription() != null
          ? payment.subscription()
          : subscription == null ? null : subscription.id();
      String checkoutId = first(
          payment == null ? null : payment.externalReference(),
          first(
              checkout == null ? null : checkout.externalReference(),
              subscription == null ? null : subscription.externalReference()));
      String providerCheckoutId = first(
          payment == null ? null : payment.checkoutSession(),
          checkout == null ? null : checkout.id());
      OffsetDateTime periodStart = date(payment == null ? null : payment.dueDate());
      return new PaymentWebhookEvent(
          webhook.id(),
          eventType,
          new String(raw, StandardCharsets.UTF_8),
          subscriptionId,
          null,
          checkoutId,
          providerCheckoutId,
          periodStart,
          null,
          null);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error("Failed to parse an Asaas webhook.", exception);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event.");
    }
  }

  private BillingProviderEventType eventType(String event) {
    return switch (event) {
      case "SUBSCRIPTION_CREATED" -> BillingProviderEventType.SUBSCRIPTION_LINKED;
      case "PAYMENT_CONFIRMED" -> BillingProviderEventType.COMPLETED;
      case "PAYMENT_OVERDUE", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED",
          "PAYMENT_REPROVED_BY_RISK_ANALYSIS", "PAYMENT_REFUNDED",
          "PAYMENT_CHARGEBACK_REQUESTED" -> BillingProviderEventType.PAYMENT_FAILED;
      case "SUBSCRIPTION_UPDATED" -> BillingProviderEventType.PLAN_CHANGED;
      case "SUBSCRIPTION_INACTIVATED", "SUBSCRIPTION_DELETED" -> BillingProviderEventType.CANCELED;
      default -> BillingProviderEventType.IGNORED;
    };
  }

  private String cycle(String interval) {
    return switch (interval) {
      case "MONTH" -> "MONTHLY";
      case "YEAR" -> "YEARLY";
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan interval is not available.");
    };
  }

  private BigDecimal amount(int minorUnits) {
    return BigDecimal.valueOf(minorUnits, 2).setScale(2, RoundingMode.UNNECESSARY);
  }

  private OffsetDateTime date(String value) {
    return value == null ? null : LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
  }

  private String first(String left, String right) {
    return left != null ? left : right;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private <T> T request(String method, String path, Object body, Class<T> responseType) {
    if (apiKey.isBlank()) {
      throw unavailable("Asaas is not configured.");
    }
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(apiUrl + path))
          .header("access_token", apiKey)
          .header("accept", "application/json")
          .header("Content-Type", "application/json");
      if ("DELETE".equals(method)) {
        builder.DELETE();
      } else {
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(
            json.writeValueAsString(body));
        builder.method(method, publisher);
      }
      HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.error(
            "Asaas request was rejected. path={}, status={}, responseBody={}, providerRequestId={}",
            path,
            response.statusCode(),
            sanitize(response.body()),
            response.headers().firstValue("request-id").orElse(null));
        throw unavailable("Asaas rejected the operation.");
      }
      if (responseType == Object.class || response.body() == null || response.body().isBlank()) {
        return null;
      }
      return json.readValue(response.body(), responseType);
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error("Asaas request failed. path={}", path, exception);
      throw unavailable("Asaas request failed.");
    }
  }

  private String sanitize(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= 4000 ? value : value.substring(0, 4000);
  }

  private ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
