package com.arkana.integration.efi;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class EfiProvider implements PaymentProvider {
  private final String clientId;
  private final String clientSecret;
  private final String chargesUrl;
  private final String pixUrl;
  private final String pixKey;
  private final boolean pixAutomaticEnabled;
  private final String certificatePath;
  private final String certificatePassword;
  private final String chargesWebhookSecret;
  private final String pixWebhookSecret;
  private final String webhookBaseUrl;
  private final ObjectMapper json;
  private final Clock clock;
  private final HttpClient chargesHttp;
  private volatile HttpClient pixHttp;
  private volatile AccessToken chargesToken;
  private volatile AccessToken pixToken;

  public EfiProvider(
      @Value("${arkana.efi.client-id:}") String clientId,
      @Value("${arkana.efi.client-secret:}") String clientSecret,
      @Value("${arkana.efi.charges-url:https://cobrancas-h.api.efipay.com.br}") String chargesUrl,
      @Value("${arkana.efi.pix-url:https://pix-h.api.efipay.com.br}") String pixUrl,
      @Value("${arkana.efi.pix-key:}") String pixKey,
      @Value("${arkana.efi.pix-automatic-enabled:false}") boolean pixAutomaticEnabled,
      @Value("${arkana.efi.certificate-path:}") String certificatePath,
      @Value("${arkana.efi.certificate-password:}") String certificatePassword,
      @Value("${arkana.efi.charges-webhook-secret:}") String chargesWebhookSecret,
      @Value("${arkana.efi.pix-webhook-secret:}") String pixWebhookSecret,
      @Value("${arkana.webhook-base-url:http://localhost:8080}") String webhookBaseUrl,
      ObjectMapper json,
      Clock clock) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.chargesUrl = trimSlash(chargesUrl);
    this.pixUrl = trimSlash(pixUrl);
    this.pixKey = pixKey;
    this.pixAutomaticEnabled = pixAutomaticEnabled;
    this.certificatePath = certificatePath;
    this.certificatePassword = certificatePassword;
    this.chargesWebhookSecret = chargesWebhookSecret;
    this.pixWebhookSecret = pixWebhookSecret;
    this.webhookBaseUrl = trimSlash(webhookBaseUrl);
    this.json = json;
    this.clock = clock;
    this.chargesHttp = HttpClient.newHttpClient();
  }

  @Override
  public BillingProvider provider() {
    return BillingProvider.EFI;
  }

  @Override
  public Set<BillingPaymentMethod> supportedPaymentMethods() {
    return pixAutomaticEnabled
        ? Set.of(BillingPaymentMethod.CARD, BillingPaymentMethod.PIX_AUTOMATIC)
        : Set.of(BillingPaymentMethod.CARD);
  }

  @Override
  public boolean requiresPlanMapping() {
    return true;
  }

  @Override
  public boolean requiresPlanMapping(BillingPaymentMethod paymentMethod) {
    return paymentMethod == BillingPaymentMethod.CARD;
  }

  @Override
  public boolean supportsDeferredFirstCharge() {
    return true;
  }

  @Override
  public Checkout createCheckout(CreateCheckout command) {
    return switch (command.paymentMethod()) {
      case CARD -> createCardSubscription(command);
      case PIX_AUTOMATIC -> createPixSubscription(command);
    };
  }

  @Override
  public void cancel(String subscriptionId) {
    cancel(subscriptionId, BillingPaymentMethod.CARD);
  }

  @Override
  public void cancel(String subscriptionId, BillingPaymentMethod paymentMethod) {
    if (paymentMethod == BillingPaymentMethod.PIX_AUTOMATIC) {
      requestPix("PATCH", "/v2/rec/" + encode(subscriptionId), Map.of("status", "CANCELADA"));
      return;
    }
    requestCharges("PUT", "/v1/subscription/" + encode(subscriptionId) + "/cancel", Map.of());
  }

  @Override
  public void changePlan(ChangePlan command) {
    if (command.paymentMethod() == BillingPaymentMethod.PIX_AUTOMATIC) {
      requestPix(
          "PATCH",
          "/v2/rec/" + encode(command.subscriptionId()),
          Map.of("valor", Map.of("valorRec", amount(command.plan().amount()))));
      return;
    }
    if (command.plan().providerProductId() == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Efí plan mapping is missing.");
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("plan_id", Long.valueOf(command.plan().providerProductId()));
    body.put("items", List.of(item(command.plan())));
    requestCharges("PUT", "/v1/subscription/" + encode(command.subscriptionId()), body);
  }

  @Override
  public PaymentWebhookEvent verifyWebhook(byte[] raw, String suppliedSecret) {
    String value = new String(raw, StandardCharsets.UTF_8).trim();
    return value.startsWith("{")
        ? verifyPixWebhook(value, suppliedSecret)
        : verifyChargesWebhook(value, suppliedSecret);
  }

  public void createRecurringPixCharge(
      String recurrenceId,
      String transactionId,
      OffsetDateTime dueAt,
      int amount) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("idRec", recurrenceId);
    body.put("calendario", Map.of("dataDeVencimento", dueAt.toLocalDate().toString()));
    body.put("valor", Map.of("original", amount(amount)));
    body.put("ajusteDiaUtil", true);
    requestPix("PUT", "/v2/cobr/" + encode(transactionId), body);
  }

  @Override
  public void updateFutureCharge(
      String subscriptionId,
      String chargeId,
      OffsetDateTime dueAt,
      int amount) {
    createRecurringPixCharge(subscriptionId, chargeId, dueAt, amount);
  }

  private Checkout createCardSubscription(CreateCheckout command) {
    requireConfigured(command.plan().providerProductId(), "Efí card plan is not configured.");
    Map<String, Object> customer = customer(command);
    Map<String, Object> address = address(command.payer());
    Map<String, Object> creditCard = new LinkedHashMap<>();
    creditCard.put("customer", customer);
    creditCard.put("billing_address", address);
    creditCard.put("payment_token", command.paymentToken());
    long trialDays = Math.max(
        0,
        ChronoUnit.DAYS.between(
            LocalDate.now(clock),
            command.firstChargeAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate()));
    if (trialDays > 0) {
      creditCard.put("trial_days", trialDays);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("items", List.of(item(command.plan())));
    body.put("metadata", Map.of(
        "custom_id", command.checkoutId(),
        "notification_url", chargesWebhookUrl()));
    body.put("payment", Map.of("credit_card", creditCard));
    JsonNode data = data(requestCharges(
        "POST",
        "/v1/plan/" + encode(command.plan().providerProductId()) + "/subscription/one-step",
        body));
    String subscriptionId = requiredText(data, "subscription_id");
    if ("unpaid".equals(data.path("charge").path("status").asText())) {
      cancelRejectedCardSubscription(subscriptionId);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "The card was declined. Check the card details or use another card.");
    }
    String chargeId = data.path("charge").path("id").asText(subscriptionId);
    return new Checkout(
        chargeId,
        subscriptionId,
        ActionType.PENDING_CONFIRMATION,
        null,
        null,
        null,
        OffsetDateTime.now(clock).plusMinutes(30));
  }

  private void cancelRejectedCardSubscription(String subscriptionId) {
    try {
      requestCharges(
          "PUT",
          "/v1/subscription/" + encode(subscriptionId) + "/cancel",
          Map.of());
    } catch (RuntimeException exception) {
      log.error(
          "Could not cancel an Efí subscription created from a refused card. subscriptionId={}",
          subscriptionId,
          exception);
    }
  }

  private Checkout createPixSubscription(CreateCheckout command) {
    requireConfigured(pixKey, "Efí Pix key is not configured.");
    JsonNode location = requestPix("POST", "/v2/locrec", Map.of());
    long locationId = location.path("id").asLong();
    if (locationId == 0) {
      throw unavailable("Efí returned an incomplete Pix location.");
    }
    boolean immediate = !command.firstChargeAt().isAfter(OffsetDateTime.now(clock).plusMinutes(1));
    String immediateTransactionId = immediate ? transactionId(command.checkoutId()) : null;
    if (immediate) {
      Map<String, Object> charge = new LinkedHashMap<>();
      charge.put("calendario", Map.of("expiracao", 1800));
      charge.put("valor", Map.of("original", amount(command.plan().amount())));
      charge.put("chave", pixKey);
      charge.put("solicitacaoPagador", "Assinatura " + command.plan().name());
      requestPix("PUT", "/v2/cob/" + immediateTransactionId, charge);
    }
    Map<String, Object> recurrence = new LinkedHashMap<>();
    recurrence.put("vinculo", Map.of(
        "contrato", command.checkoutId(),
        "devedor", Map.of(
            "cpf", command.payer().document(),
            "nome", command.payer().name()),
        "objeto", command.plan().name()));
    recurrence.put("calendario", Map.of(
        "dataInicial", command.firstChargeAt().toLocalDate().toString(),
        "periodicidade", periodicity(command.plan().interval())));
    recurrence.put("valor", Map.of("valorRec", amount(command.plan().amount())));
    recurrence.put("politicaRetentativa", "PERMITE_3R_7D");
    recurrence.put("loc", locationId);
    if (immediateTransactionId != null) {
      recurrence.put("ativacao", Map.of("dadosJornada", Map.of("txid", immediateTransactionId)));
    }
    JsonNode created = requestPix("POST", "/v2/rec", recurrence);
    String recurrenceId = requiredText(created, "idRec");
    JsonNode details = requestPix(
        "GET",
        "/v2/rec/" + encode(recurrenceId)
            + (immediateTransactionId == null ? "" : "?txid=" + immediateTransactionId),
        null);
    String copyPasteCode = details.path("dadosQR").path("pixCopiaECola").asText(null);
    JsonNode qrCode = requestPix("GET", "/v2/loc/" + locationId + "/qrcode", null);
    return new Checkout(
        immediateTransactionId == null ? recurrenceId : immediateTransactionId,
        recurrenceId,
        ActionType.PIX_QR_CODE,
        qrCode.path("linkVisualizacao").asText(null),
        copyPasteCode == null ? qrCode.path("qrcode").asText(null) : copyPasteCode,
        qrCode.path("imagemQrcode").asText(null),
        OffsetDateTime.now(clock).plusMinutes(30));
  }

  private PaymentWebhookEvent verifyChargesWebhook(String token, String secret) {
    requireSecret(chargesWebhookSecret, secret);
    if (token.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event.");
    }
    JsonNode entries = data(requestCharges("GET", "/v1/notification/" + encode(token), null));
    if (!entries.isArray() || entries.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event.");
    }
    JsonNode entry = entries.get(entries.size() - 1);
    String type = entry.path("type").asText("");
    String status = entry.path("status").path("current").asText("");
    BillingProviderEventType eventType = chargesEventType(type, status);
    String subscriptionId = entry.path("identifiers").path("subscription_id").asText(null);
    String chargeId = entry.path("identifiers").path("charge_id").asText(null);
    String checkoutId = entry.path("custom_id").asText(null);
    String historyId = entry.path("id").asText(status);
    return new PaymentWebhookEvent(
        "charges:" + digest(token + ':' + historyId),
        eventType,
        sanitized("charges", type, status, subscriptionId, chargeId),
        subscriptionId,
        null,
        checkoutId,
        chargeId,
        null,
        null,
        null,
        BillingPaymentMethod.CARD);
  }

  private PaymentWebhookEvent verifyPixWebhook(String payload, String secret) {
    requireSecret(pixWebhookSecret, secret);
    try {
      JsonNode root = json.readTree(payload);
      if (root.path("recs").isArray() && !root.path("recs").isEmpty()) {
        JsonNode event = root.path("recs").get(0);
        String recurrenceId = requiredText(event, "idRec");
        String status = requiredText(event, "status");
        String updatedAt = latestUpdate(event);
        return new PaymentWebhookEvent(
            "pix-rec:" + recurrenceId + ':' + status + ':' + updatedAt,
            recurrenceEventType(status),
            sanitized("pix-rec", "rec", status, recurrenceId, null),
            recurrenceId,
            null,
            null,
            recurrenceId,
            null,
            null,
            null,
            BillingPaymentMethod.PIX_AUTOMATIC);
      }
      if (root.path("cobsr").isArray() && !root.path("cobsr").isEmpty()) {
        JsonNode event = root.path("cobsr").get(0);
        String recurrenceId = requiredText(event, "idRec");
        String transactionId = requiredText(event, "txid");
        String status = requiredText(event, "status");
        return new PaymentWebhookEvent(
            "pix-charge:" + transactionId + ':' + status + ':' + latestUpdate(event),
            pixChargeEventType(status),
            sanitized("pix-charge", "cobsr", status, recurrenceId, transactionId),
            recurrenceId,
            null,
            null,
            transactionId,
            null,
            null,
            null,
            BillingPaymentMethod.PIX_AUTOMATIC);
      }
      if (root.path("pix").isArray() && !root.path("pix").isEmpty()) {
        JsonNode event = root.path("pix").get(0);
        String transactionId = requiredText(event, "txid");
        return new PaymentWebhookEvent(
            "pix:" + event.path("endToEndId").asText(transactionId),
            BillingProviderEventType.COMPLETED,
            sanitized("pix", "pix", "CONCLUIDA", null, transactionId),
            null,
            null,
            null,
            transactionId,
            dateTime(event.path("horario").asText(null)),
            null,
            null,
            BillingPaymentMethod.PIX_AUTOMATIC);
      }
      return ignored("pix:ignored:" + digest(payload));
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook event.");
    }
  }

  private BillingProviderEventType chargesEventType(String type, String status) {
    if ("subscription".equals(type) && "active".equals(status)) {
      return BillingProviderEventType.SUBSCRIPTION_LINKED;
    }
    if ("paid".equals(status) || "settled".equals(status)) {
      return BillingProviderEventType.COMPLETED;
    }
    if ("unpaid".equals(status)) {
      return BillingProviderEventType.PAYMENT_FAILED;
    }
    if (Set.of("canceled", "expired").contains(status)) {
      return BillingProviderEventType.CANCELED;
    }
    return BillingProviderEventType.IGNORED;
  }

  private BillingProviderEventType recurrenceEventType(String status) {
    return switch (status) {
      case "APROVADA" -> BillingProviderEventType.SUBSCRIPTION_LINKED;
      case "CANCELADA", "REJEITADA", "EXPIRADA" -> BillingProviderEventType.CANCELED;
      default -> BillingProviderEventType.IGNORED;
    };
  }

  private BillingProviderEventType pixChargeEventType(String status) {
    return switch (status) {
      case "CONCLUIDA" -> BillingProviderEventType.RENEWED;
      // A rejection can still be followed by one of the PERMITE_3R_7D retries.
      // Only terminal expiry exhausts the provider-managed retry window.
      case "EXPIRADA" -> BillingProviderEventType.PAYMENT_FAILED;
      case "CANCELADA" -> BillingProviderEventType.CANCELED;
      default -> BillingProviderEventType.IGNORED;
    };
  }

  private PaymentWebhookEvent ignored(String id) {
    return new PaymentWebhookEvent(
        id,
        BillingProviderEventType.IGNORED,
        "{\"provider\":\"EFI\",\"ignored\":true}",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private JsonNode requestCharges(String method, String path, Object body) {
    return request(chargesHttp, chargesUrl, false, method, path, body);
  }

  private JsonNode requestPix(String method, String path, Object body) {
    return request(pixHttp(), pixUrl, true, method, path, body);
  }

  private JsonNode request(
      HttpClient http,
      String baseUrl,
      boolean pix,
      String method,
      String path,
      Object body) {
    requireConfigured(clientId, "Efí is not configured.");
    requireConfigured(clientSecret, "Efí is not configured.");
    try {
      String token = accessToken(http, baseUrl, pix);
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
          .header("Authorization", "Bearer " + token)
          .header("Accept", "application/json")
          .header("Content-Type", "application/json");
      if ("GET".equals(method)) {
        builder.GET();
      } else {
        String payload = body == null ? "{}" : json.writeValueAsString(body);
        builder.method(method, HttpRequest.BodyPublishers.ofString(payload));
      }
      HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.error(
            "Efí request was rejected. api={}, path={}, status={}, responseBody={}, providerRequestId={}",
            pix ? "pix" : "charges",
            path,
            response.statusCode(),
            response.body(),
            response.headers().firstValue("x-request-id").orElse(null));
        throw unavailable("Efí could not process the payment right now. Try again or contact support.");
      }
      return response.body() == null || response.body().isBlank()
          ? json.createObjectNode()
          : json.readTree(response.body());
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error("Efí request failed. api={}, path={}", pix ? "pix" : "charges", path, exception);
      throw unavailable("Efí request failed.");
    }
  }

  private String accessToken(HttpClient http, String baseUrl, boolean pix) throws Exception {
    AccessToken cached = pix ? pixToken : chargesToken;
    OffsetDateTime now = OffsetDateTime.now(clock);
    if (cached != null && cached.expiresAt().isAfter(now.plusSeconds(30))) {
      return cached.value();
    }
    synchronized (this) {
      cached = pix ? pixToken : chargesToken;
      if (cached != null && cached.expiresAt().isAfter(now.plusSeconds(30))) {
        return cached.value();
      }
      String basic = Base64.getEncoder().encodeToString(
          (clientId + ':' + clientSecret).getBytes(StandardCharsets.UTF_8));
      String authorizationPath = pix ? "/oauth/token" : "/v1/authorize";
      HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + authorizationPath))
          .header("Authorization", "Basic " + basic)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString("{\"grant_type\":\"client_credentials\"}"))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        throw unavailable("Efí authorization failed.");
      }
      JsonNode tokenResponse = json.readTree(response.body());
      AccessToken refreshed = new AccessToken(
          requiredText(tokenResponse, "access_token"),
          now.plusSeconds(Math.max(60, tokenResponse.path("expires_in").asLong(300))));
      if (pix) {
        pixToken = refreshed;
      } else {
        chargesToken = refreshed;
      }
      return refreshed.value();
    }
  }

  private HttpClient pixHttp() {
    if (pixHttp != null) {
      return pixHttp;
    }
    synchronized (this) {
      if (pixHttp != null) {
        return pixHttp;
      }
      requireConfigured(certificatePath, "Efí Pix certificate is not configured.");
      try {
        char[] password = certificatePassword.toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream stream = Files.newInputStream(Path.of(certificatePath))) {
          keyStore.load(stream, password);
        }
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        pixHttp = HttpClient.newBuilder().sslContext(sslContext).build();
        return pixHttp;
      } catch (Exception exception) {
        log.error("Could not initialize the Efí Pix mTLS client.", exception);
        throw unavailable("Efí Pix certificate could not be loaded.");
      }
    }
  }

  private Map<String, Object> customer(CreateCheckout command) {
    return Map.of(
        "name", command.payer().name(),
        "cpf", command.payer().document(),
        "email", efiEmail(command.email()),
        "phone_number", command.payer().phoneNumber());
  }

  private String efiEmail(String email) {
    int at = email.lastIndexOf('@');
    int plus = email.indexOf('+');
    return plus >= 0 && plus < at
        ? email.substring(0, plus) + email.substring(at)
        : email;
  }

  private Map<String, Object> address(Payer payer) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("street", payer.billingAddress().street());
    value.put("number", payer.billingAddress().number());
    value.put("neighborhood", payer.billingAddress().neighborhood());
    value.put("zipcode", payer.billingAddress().zipCode());
    value.put("city", payer.billingAddress().city());
    value.put("state", payer.billingAddress().state());
    if (payer.billingAddress().complement() != null) {
      value.put("complement", payer.billingAddress().complement());
    }
    return value;
  }

  private Map<String, Object> item(Plan plan) {
    return Map.of("name", plan.name(), "value", plan.amount(), "amount", 1);
  }

  private JsonNode data(JsonNode response) {
    return response.has("data") ? response.path("data") : response;
  }

  private String requiredText(JsonNode node, String field) {
    String value = node.path(field).asText(null);
    if (value == null || value.isBlank()) {
      throw unavailable("Efí returned an incomplete response.");
    }
    return value;
  }

  private String latestUpdate(JsonNode event) {
    JsonNode updates = event.path("atualizacao");
    return updates.isArray() && !updates.isEmpty()
        ? updates.get(updates.size() - 1).path("data").asText("unknown")
        : "unknown";
  }

  private String sanitized(
      String source,
      String type,
      String status,
      String subscriptionId,
      String chargeId) {
    try {
      Map<String, Object> safe = new LinkedHashMap<>();
      safe.put("provider", "EFI");
      safe.put("source", source);
      safe.put("type", type);
      safe.put("status", status);
      safe.put("subscriptionId", subscriptionId);
      safe.put("chargeId", chargeId);
      return json.writeValueAsString(safe);
    } catch (Exception exception) {
      return "{\"provider\":\"EFI\"}";
    }
  }

  private String chargesWebhookUrl() {
    return webhookBaseUrl + "/v1/webhook/payment/efi/charges?webhookSecret="
        + encode(chargesWebhookSecret);
  }

  private String periodicity(String interval) {
    return switch (interval) {
      case "MONTH" -> "MENSAL";
      case "YEAR" -> "ANUAL";
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan interval is not available.");
    };
  }

  private String amount(int minorUnits) {
    return BigDecimal.valueOf(minorUnits, 2).toPlainString();
  }

  private String transactionId(String checkoutId) {
    return checkoutId.replace("-", "").substring(0, 32);
  }

  private String digest(String value) {
    try {
      return java.util.HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private OffsetDateTime dateTime(String value) {
    return value == null ? null : OffsetDateTime.parse(value);
  }

  private void requireSecret(String expected, String supplied) {
    if (expected.isBlank()
        || supplied == null
        || !MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        supplied.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }
  }

  private void requireConfigured(String value, String message) {
    if (value == null || value.isBlank()) {
      throw unavailable(message);
    }
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String trimSlash(String value) {
    return value.replaceAll("/+$", "");
  }

  private ResponseStatusException unavailable(String message) {
    return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
  }

  private record AccessToken(String value, OffsetDateTime expiresAt) {
  }

}
