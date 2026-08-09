package com.arkana.controller;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingCheckoutStatus;
import com.arkana.domain.Profile;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingCheckoutRepository;
import com.arkana.repository.BillingProviderEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentIntegrationControllerIT extends BaseControllerIT {
    private static final UUID MONTHLY_PLAN_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Autowired
    private BillingAccountRepository accountRepository;
    @Autowired
    private BillingCheckoutRepository checkoutRepository;
    @Autowired
    private BillingProviderEventRepository eventRepository;

    @Test
    void shouldApplySignedWebhookOnlyToTargetBillingAccountAndDeduplicateIt() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        BillingAccount firstAccount = billingAccount(firstUser, BillingAccountStatus.PENDING_PAYMENT);
        BillingAccount secondAccount = billingAccount(secondUser, BillingAccountStatus.PENDING_PAYMENT);
        BillingCheckout firstCheckout =
            entityGeneratorService.randomBillingCheckout(firstAccount, MONTHLY_PLAN_ID, UUID.randomUUID());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String payload = payload("isolated-event", firstCheckout.getId(), now);
        String signature = hmac(payload, "test-hmac-key");

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvcPerform(post("/v1/webhook/payment/abacatepay?webhookSecret=test-webhook-secret")
                    .header("X-Webhook-Signature", signature)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
        }

        BillingAccount persistedFirst = accountRepository.findById(firstAccount.getId()).orElseThrow();
        BillingAccount persistedSecond = accountRepository.findById(secondAccount.getId()).orElseThrow();
        assertThat(persistedFirst.getStatus()).isEqualTo(BillingAccountStatus.ACTIVE);
        assertThat(persistedSecond.getStatus()).isEqualTo(BillingAccountStatus.PENDING_PAYMENT);
        assertThat(checkoutRepository.findById(firstCheckout.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingCheckoutStatus.COMPLETED);
        assertThat(eventRepository.countByProviderEventId("isolated-event")).isEqualTo(1);
    }

    @Test
    void shouldNotChangeAnyAccountWhenWebhookCredentialsAreInvalid() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        BillingAccount firstAccount = billingAccount(firstUser, BillingAccountStatus.PENDING_PAYMENT);
        BillingAccount secondAccount = billingAccount(secondUser, BillingAccountStatus.PENDING_PAYMENT);
        BillingCheckout firstCheckout =
            entityGeneratorService.randomBillingCheckout(firstAccount, MONTHLY_PLAN_ID, UUID.randomUUID());
        String payload = payload("invalid-event", firstCheckout.getId(), OffsetDateTime.now(ZoneOffset.UTC));

        mockMvcPerform(post("/v1/webhook/payment/abacatepay?webhookSecret=wrong-secret")
                .header("X-Webhook-Signature", hmac(payload, "test-hmac-key"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());
        mockMvcPerform(post("/v1/webhook/payment/abacatepay?webhookSecret=test-webhook-secret")
                .header("X-Webhook-Signature", Base64.getEncoder().encodeToString("invalid".getBytes()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());

        assertThat(accountRepository.findById(firstAccount.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingAccountStatus.PENDING_PAYMENT);
        assertThat(accountRepository.findById(secondAccount.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingAccountStatus.PENDING_PAYMENT);
        assertThat(eventRepository.countByProviderEventId("invalid-event")).isZero();
    }

    private String payload(String eventId, UUID checkoutId, OffsetDateTime now) {
        return "{\"id\":\"" + eventId
            + "\",\"event\":\"subscription.completed\",\"data\":{\"subscription\":{\"id\":\"subscription-"
            + eventId + "\",\"currentPeriodStart\":\"" + now
            + "\",\"currentPeriodEnd\":\"" + now.plusMonths(1)
            + "\"},\"checkout\":{\"externalId\":\"" + checkoutId + "\"}}}";
    }

    private String hmac(String payload, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private BillingAccount billingAccount(Profile owner, BillingAccountStatus status) {
        BillingAccount account = entityGeneratorService.randomBillingAccount(owner);
        account.setStatus(status);
        account.setTrialStartedAt(null);
        account.setTrialEndsAt(null);
        account.setCurrentPeriodStart(null);
        account.setCurrentPeriodEnd(null);
        account.setCancelAtPeriodEnd(false);
        account.setOverrideEndsAt(null);
        accountRepository.flush();
        return account;
    }
}
