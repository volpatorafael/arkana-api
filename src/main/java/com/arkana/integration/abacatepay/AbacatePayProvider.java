package com.arkana.integration.abacatepay;

import com.arkana.integration.PaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AbacatePayProvider implements PaymentProvider {
  private static final Set<String> SUPPORTED_EVENTS = Set.of(
      "subscription.trial_started",
      "subscription.completed",
      "subscription.renewed",
      "subscription.payment_failed",
      "subscription.cancelled",
      "subscription.plan_changed");

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
  public Checkout createCheckout(String account, String checkout, String product, String method) {
    LinkedHashMap<String, Object> body = new LinkedHashMap<>();
    body.put("completionUrl", appUrl + "/app?billing=success");
    body.put("returnUrl", appUrl + "/app?billing=return");
    body.put("externalId", checkout);
    body.put("items", List.of(Map.of("id", product, "quantity", 1)));
    body.put("methods", List.of("PIX_AUTOMATIC".equals(method) ? "PIX" : "CARD"));
    body.put("metadata", Map.of("billingAccountId", account, "checkoutId", checkout));

    JsonNode data = request("/subscriptions/create", body);
    String id = text(data, "id");
    String url = text(data, "url");
    if (id == null || url == null) {
      throw unavailable("AbacatePay returned an incomplete checkout.");
    }
    String expiry = text(data, "expiresAt");
    OffsetDateTime expiresAt = expiry == null
        ? OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30)
        : OffsetDateTime.parse(expiry);
    return new Checkout(id, url, expiresAt);
  }

  @Override
  public void cancel(String id) {
    request("/subscriptions/cancel", Map.of("id", id));
  }

  @Override
  public void changePlan(String id, String product) {
    request("/subscriptions/change-plan", Map.of("id", id, "productId", product, "quantity", 1));
  }

  @Override
  public Map<String, Object> verifyWebhook(byte[] raw, String signature) {
    if (hmacKey.isBlank()) {
      throw unavailable("AbacatePay webhook is not configured.");
    }
    try {
      verifySignature(raw, signature);

      JsonNode root = json.readTree(raw);
      String id = text(root, "id");
      String event = text(root, "event");
      if (id == null || event == null || !SUPPORTED_EVENTS.contains(event)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event.");
      }

      JsonNode data = root.path("data");
      JsonNode subscription = data.path("subscription");
      JsonNode checkout = data.path("checkout");
      JsonNode metadata = checkout.path("metadata");
      LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
      normalized.put("id", id);
      normalized.put("event", event);
      normalized.put("payload", new String(raw, StandardCharsets.UTF_8));
      normalized.put("subscriptionId", text(subscription, "id"));
      normalized.put(
          "productId",
          first(text(subscription, "productId"), text(subscription.path("product"), "id")));
      normalized.put("checkoutId", first(text(checkout, "externalId"), text(metadata, "checkoutId")));
      normalized.put("periodStart", date(subscription, "currentPeriodStart"));
      normalized.put("periodEnd", firstDate(subscription, "currentPeriodEnd", "nextBillingAt"));
      normalized.put("trialEnd", date(subscription, "trialEndsAt"));
      return normalized;
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
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

  private JsonNode request(String path, Object body) {
    if (apiKey.isBlank()) {
      throw unavailable("AbacatePay is not configured.");
    }
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.abacatepay.com/v2" + path))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode root = json.readTree(response.body());
      if (response.statusCode() >= 300 || !root.path("error").isMissingNode()) {
        throw unavailable("AbacatePay rejected the operation.");
      }
      return root.path("data");
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw unavailable("AbacatePay request failed.");
    }
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
  }

  private String first(String left, String right) {
    return left != null ? left : right;
  }

  private OffsetDateTime date(JsonNode node, String field) {
    String value = text(node, field);
    return value == null ? null : OffsetDateTime.parse(value);
  }

  private OffsetDateTime firstDate(JsonNode node, String first, String second) {
    OffsetDateTime value = date(node, first);
    return value != null ? value : date(node, second);
  }

  private ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }
}
