package com.arkana.controller;

import com.arkana.dto.billing.WebhookAcceptedResponse;
import com.arkana.domain.BillingProvider;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@RestController
@RequestMapping("/v1")
public class PaymentIntegrationController {
  private final BillingService billing;
  private final String webhookSecret;

  public PaymentIntegrationController(
      BillingService billing,
      @Value("${arkana.abacatepay.webhook-secret:}") String webhookSecret) {
    this.billing = billing;
    this.webhookSecret = webhookSecret;
  }

  @PostMapping("/webhook/payment/abacatepay")
  WebhookAcceptedResponse webhook(
      @RequestParam String webhookSecret,
      @RequestHeader("X-Webhook-Signature") String signature,
      @RequestBody byte[] raw) {
    requireSecret(this.webhookSecret, webhookSecret);
    billing.webhook(BillingProvider.ABACATEPAY, raw, signature);
    return new WebhookAcceptedResponse(true);
  }

  @PostMapping("/webhook/payment/asaas")
  WebhookAcceptedResponse asaasWebhook(
      @RequestHeader("asaas-access-token") String token,
      @RequestBody byte[] raw) {
    billing.webhook(BillingProvider.ASAAS, raw, token);
    return new WebhookAcceptedResponse(true);
  }

  private void requireSecret(String expected, String supplied) {
    if (expected.isBlank()
        || !MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        supplied.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }
  }
}
