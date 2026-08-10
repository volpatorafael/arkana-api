package com.arkana.controller;

import com.arkana.domain.BillingProvider;
import com.arkana.dto.billing.WebhookAcceptedResponse;
import com.arkana.service.BillingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/v1/webhook/payment/efi")
public class PaymentWebhookEfiController {
  private final BillingService billing;
  private final String pixHmacKey;
  private final boolean pixRequireMtls;

  public PaymentWebhookEfiController(
      BillingService billing,
      @Value("${arkana.efi.pix-webhook-hmac-key:}") String pixHmacKey,
      @Value("${arkana.efi.pix-require-mtls:false}") boolean pixRequireMtls) {
    this.billing = billing;
    this.pixHmacKey = pixHmacKey;
    this.pixRequireMtls = pixRequireMtls;
  }

  @PostMapping(value = "/charges", consumes = "application/x-www-form-urlencoded")
  WebhookAcceptedResponse chargesWebhook(
      @RequestParam String webhookSecret,
      @RequestParam String notification) {
    billing.webhook(
        BillingProvider.EFI,
        notification.getBytes(StandardCharsets.UTF_8),
        webhookSecret);
    return new WebhookAcceptedResponse(true);
  }

  @PostMapping("/pix")
  WebhookAcceptedResponse pixWebhook(
      @RequestParam String webhookSecret,
      @RequestHeader("X-Efi-Signature") String signature,
      @RequestHeader(value = "X-Client-Certificate-Verified", required = false) String clientCertificate,
      @RequestBody byte[] raw) {
    if (pixRequireMtls && !"SUCCESS".equals(clientCertificate)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Client certificate is required.");
    }
    requireHmac(raw, signature);
    billing.webhook(BillingProvider.EFI, raw, webhookSecret);
    return new WebhookAcceptedResponse(true);
  }

  private void requireHmac(byte[] raw, String supplied) {
    if (pixHmacKey.isBlank() || supplied == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(pixHmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected = HexFormat.of().formatHex(mac.doFinal(raw)).getBytes(StandardCharsets.US_ASCII);
      byte[] actual = supplied.toLowerCase().getBytes(StandardCharsets.US_ASCII);
      if (!MessageDigest.isEqual(expected, actual)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
      }
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }
  }
}
