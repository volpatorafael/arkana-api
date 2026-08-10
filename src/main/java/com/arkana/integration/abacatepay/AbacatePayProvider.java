package com.arkana.integration.abacatepay;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

@Component
@Slf4j
public class AbacatePayProvider implements PaymentProvider {
  private final String apiKey;
  private final String hmacKey;
  private final String appUrl;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper json;

  public AbacatePayProvider(
      @Value("${arkana.abacatepay.api-key:}") String apiKey,
      @Value("${arkana.abacatepay.webhook-hmac-key:}") String hmacKey,
      @Value("${arkana.app-url:http://localhost:5173}") String appUrl,
      ObjectMapper json) {
    this.apiKey = apiKey;
    this.hmacKey = hmacKey;
    this.appUrl = appUrl.replaceAll("/+$", "");
    this.json = json;
  }

  @Override
  public Checkout createCheckout(
      String account,
      String checkout,
      String product,
      BillingPaymentMethod method) {
    AbacatePayCreateSubscriptionRequest body = new AbacatePayCreateSubscriptionRequest(
        appUrl + "/app?billing=success",
        appUrl + "/app?billing=return",
        checkout,
        List.of(new AbacatePayCreateSubscriptionRequest.Item(product, 1)),
        List.of(method == BillingPaymentMethod.PIX_AUTOMATIC ? "PIX" : "CARD"),
        new AbacatePayCreateSubscriptionRequest.Metadata(account, checkout));
    AbacatePayCreateSubscriptionResponse response = request(
        "/subscriptions/create",
        body,
        new TypeReference<>() {
        });
    if (response == null || response.id() == null || response.url() == null) {
      log.error(
          "AbacatePay returned an incomplete checkout. providerIdPresent={}, urlPresent={}, expiresAt={}",
          response != null && response.id() != null,
          response != null && response.url() != null,
          response == null ? null : response.expiresAt());
      throw unavailable("AbacatePay returned an incomplete checkout.");
    }
    OffsetDateTime expiresAt = response.expiresAt() == null
        ? OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30)
        : response.expiresAt();
    return new Checkout(response.id(), response.url(), expiresAt);
  }

  @Override
  public void cancel(String id) {
    request(
        "/subscriptions/cancel",
        new AbacatePaySubscriptionRequest(id),
        new TypeReference<AbacatePayApiResponse<Object>>() {
        });
  }

  @Override
  public void changePlan(String id, String product) {
    request(
        "/subscriptions/change-plan",
        new AbacatePayChangePlanRequest(id, product, 1),
        new TypeReference<AbacatePayApiResponse<Object>>() {
        });
  }

  @Override
  public PaymentWebhookEvent verifyWebhook(byte[] raw, String signature) {
    if (hmacKey.isBlank()) {
      log.error("AbacatePay webhook validation failed because the HMAC key is not configured.");
      throw unavailable("AbacatePay webhook is not configured.");
    }
    try {
      verifySignature(raw, signature);

      AbacatePayWebhookRequest webhook = json.readValue(raw, AbacatePayWebhookRequest.class);
      BillingProviderEventType eventType = BillingProviderEventType
          .fromEventValue(webhook.event())
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Invalid webhook event."));
      if (webhook.id() == null || webhook.id().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event.");
      }

      AbacatePayWebhookRequest.Data data = webhook.data();
      AbacatePayWebhookRequest.Subscription subscription = data == null ? null : data.subscription();
      AbacatePayWebhookRequest.Checkout checkout = data == null ? null : data.checkout();
      return new PaymentWebhookEvent(
          webhook.id(),
          eventType,
          new String(raw, StandardCharsets.UTF_8),
          subscription == null ? null : subscription.id(),
          productId(subscription),
          checkoutId(checkout),
          subscription == null ? null : subscription.currentPeriodStart(),
          periodEnd(subscription),
          subscription == null ? null : subscription.trialEndsAt());
    } catch (ResponseStatusException exception) {
      log.error(
          "AbacatePay webhook was rejected. status={}, reason={}",
          exception.getStatusCode(),
          exception.getReason());
      throw exception;
    } catch (Exception exception) {
      log.error("Failed to parse an AbacatePay webhook after signature validation.", exception);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event.");
    }
  }

  private void verifySignature(byte[] raw, String signature) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] expected = mac.doFinal(raw);
    byte[] supplied = Base64.getDecoder().decode(signature);
    if (!MessageDigest.isEqual(expected, supplied)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature.");
    }
  }

  private <T> T request(
      String path,
      Object body,
      TypeReference<AbacatePayApiResponse<T>> responseType) {
    if (apiKey.isBlank()) {
      log.error("AbacatePay request could not be sent because the API key is not configured. path={}", path);
      throw unavailable("AbacatePay is not configured.");
    }
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.abacatepay.com/v2" + path))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.error(
            "AbacatePay request was rejected. path={}, status={}, responseBody={}",
            path,
            response.statusCode(),
            response.body());
        throw unavailable("AbacatePay rejected the operation.");
      }
      AbacatePayApiResponse<T> providerResponse;
      try {
        providerResponse = json.readValue(response.body(), responseType);
      } catch (Exception exception) {
        log.error(
            "Could not parse AbacatePay response. path={}, status={}, responseBody={}",
            path,
            response.statusCode(),
            response.body(),
            exception);
        throw unavailable("AbacatePay returned an invalid response.");
      }
      if (providerResponse.error() != null) {
        log.error(
            "AbacatePay returned an application error. path={}, status={}, responseBody={}",
            path,
            response.statusCode(),
            response.body());
        throw unavailable("AbacatePay rejected the operation.");
      }
      return providerResponse.data();
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error("AbacatePay request failed. path={}", path, exception);
      throw unavailable("AbacatePay request failed.");
    }
  }

  private String first(String left, String right) {
    return left != null ? left : right;
  }

  private String productId(AbacatePayWebhookRequest.Subscription subscription) {
    if (subscription == null) {
      return null;
    }
    return first(
        subscription.productId(),
        subscription.product() == null ? null : subscription.product().id());
  }

  private String checkoutId(AbacatePayWebhookRequest.Checkout checkout) {
    if (checkout == null) {
      return null;
    }
    return first(
        checkout.externalId(),
        checkout.metadata() == null ? null : checkout.metadata().checkoutId());
  }

  private OffsetDateTime periodEnd(AbacatePayWebhookRequest.Subscription subscription) {
    return subscription == null
        ? null
        : first(subscription.currentPeriodEnd(), subscription.nextBillingAt());
  }

  private OffsetDateTime first(OffsetDateTime left, OffsetDateTime right) {
    return left != null ? left : right;
  }

  private ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
