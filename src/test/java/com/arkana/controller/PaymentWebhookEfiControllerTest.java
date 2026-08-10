package com.arkana.controller;

import com.arkana.domain.BillingProvider;
import com.arkana.service.BillingService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentWebhookEfiControllerTest {
  private final BillingService billing = mock(BillingService.class);
  private final PaymentWebhookEfiController controller =
      new PaymentWebhookEfiController(billing, "pix-hmac-key", true);

  @Test
  void shouldRequireTrustedMtlsAssertionAndValidBodyHmacForPix() throws Exception {
    byte[] raw = "{\"recs\":[]}".getBytes(StandardCharsets.UTF_8);
    String signature = signature(raw);

    assertThat(controller.pixWebhook("pix-secret", signature, "SUCCESS", raw).accepted()).isTrue();
    verify(billing).webhook(BillingProvider.EFI, raw, "pix-secret");

    assertThatThrownBy(() -> controller.pixWebhook("pix-secret", signature, null, raw))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Client certificate is required");
    assertThatThrownBy(() -> controller.pixWebhook("pix-secret", "00", "SUCCESS", raw))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Invalid credentials");
  }

  private String signature(byte[] raw) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("pix-hmac-key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(raw));
  }
}
