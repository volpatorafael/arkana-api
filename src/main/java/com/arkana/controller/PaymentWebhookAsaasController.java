package com.arkana.controller;

import com.arkana.domain.BillingProvider;
import com.arkana.dto.billing.WebhookAcceptedResponse;
import com.arkana.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhook/payment/asaas")
@RequiredArgsConstructor
public class PaymentWebhookAsaasController {
  private final BillingService billing;

  @PostMapping
  WebhookAcceptedResponse webhook(
      @RequestHeader("asaas-access-token") String token,
      @RequestBody byte[] raw) {
    billing.webhook(BillingProvider.ASAAS, raw, token);
    return new WebhookAcceptedResponse(true);
  }
}
