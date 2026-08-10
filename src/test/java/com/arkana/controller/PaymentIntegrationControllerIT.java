package com.arkana.controller;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingCheckoutStatus;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.domain.Profile;
import com.arkana.integration.dto.PaymentWebhookEvent;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingCheckoutRepository;
import com.arkana.repository.BillingProviderEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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
        firstCheckout.setProvider(BillingProvider.ABACATEPAY);
        checkoutRepository.flush();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String payload = payload("isolated-event", firstCheckout.getId(), now);
        String signature = "valid-signature";
        when(abacatePayProvider.verifyWebhook(any(byte[].class), eq(signature)))
            .thenReturn(providerEvent(
                "isolated-event",
                "subscription-isolated-event",
                firstCheckout.getId(),
                now));

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
        firstCheckout.setProvider(BillingProvider.ABACATEPAY);
        checkoutRepository.flush();
        String payload = payload("invalid-event", firstCheckout.getId(), OffsetDateTime.now(ZoneOffset.UTC));
        String invalidSignature = "invalid-signature";
        when(abacatePayProvider.verifyWebhook(any(byte[].class), eq(invalidSignature)))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature."));

        mockMvcPerform(post("/v1/webhook/payment/abacatepay?webhookSecret=wrong-secret")
                .header("X-Webhook-Signature", "valid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());
        mockMvcPerform(post("/v1/webhook/payment/abacatepay?webhookSecret=test-webhook-secret")
                .header("X-Webhook-Signature", invalidSignature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());

        assertThat(accountRepository.findById(firstAccount.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingAccountStatus.PENDING_PAYMENT);
        assertThat(accountRepository.findById(secondAccount.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingAccountStatus.PENDING_PAYMENT);
        assertThat(eventRepository.countByProviderEventId("invalid-event")).isZero();
    }

    @Test
    void shouldAuthenticateAndApplyAsaasPaymentConfirmationOnlyOnce() throws Exception {
        Profile user = entityGeneratorService.randomProfile();
        BillingAccount account = billingAccount(user, BillingAccountStatus.PENDING_PAYMENT);
        BillingCheckout checkout =
            entityGeneratorService.randomBillingCheckout(account, MONTHLY_PLAN_ID, UUID.randomUUID());
        checkout.setProvider(BillingProvider.ASAAS);
        checkout.setProviderCheckoutId("chk_asaas_it");
        checkout.setStatus(BillingCheckoutStatus.PENDING);
        checkoutRepository.flush();
        String payload = "{\"id\":\"evt_asaas_it\",\"event\":\"PAYMENT_CONFIRMED\","
            + "\"payment\":{\"subscription\":\"sub_asaas_it\","
            + "\"checkoutSession\":\"chk_asaas_it\",\"dueDate\":\"2026-08-10\","
            + "\"billingType\":\"CREDIT_CARD\"}}";
        OffsetDateTime periodStart = OffsetDateTime.now(ZoneOffset.UTC);
        when(asaasProvider.verifyWebhook(any(byte[].class), eq("wrong-token")))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials."));
        when(asaasProvider.verifyWebhook(any(byte[].class), eq("test-asaas-webhook-token")))
            .thenReturn(new PaymentWebhookEvent(
                "evt_asaas_it",
                BillingProviderEventType.COMPLETED,
                payload,
                "sub_asaas_it",
                null,
                null,
                "chk_asaas_it",
                periodStart,
                periodStart.plusMonths(1),
                null));

        mockMvcPerform(post("/v1/webhook/payment/asaas")
                .header("asaas-access-token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnauthorized());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvcPerform(post("/v1/webhook/payment/asaas")
                    .header("asaas-access-token", "test-asaas-webhook-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
        }

        BillingAccount persisted = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(BillingAccountStatus.ACTIVE);
        assertThat(persisted.getCurrentProvider()).isEqualTo(BillingProvider.ASAAS);
        assertThat(checkoutRepository.findById(checkout.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingCheckoutStatus.COMPLETED);
        assertThat(eventRepository.countByProviderEventId("evt_asaas_it")).isEqualTo(1);
    }

    private String payload(String eventId, UUID checkoutId, OffsetDateTime now) {
        return "{\"id\":\"" + eventId
            + "\",\"event\":\"subscription.completed\",\"ignoredRoot\":true,\"data\":{\"subscription\":{\"id\":\"subscription-"
            + eventId + "\",\"currentPeriodStart\":\"" + now
            + "\",\"currentPeriodEnd\":\"" + now.plusMonths(1)
            + "\",\"ignoredSubscription\":true},\"checkout\":{\"externalId\":\""
            + checkoutId + "\",\"ignoredCheckout\":true}}}";
    }

    private PaymentWebhookEvent providerEvent(
        String eventId,
        String subscriptionId,
        UUID checkoutId,
        OffsetDateTime periodStart) {
        return new PaymentWebhookEvent(
            eventId,
            BillingProviderEventType.COMPLETED,
            "{\"id\":\"" + eventId + "\"}",
            subscriptionId,
            null,
            checkoutId.toString(),
            periodStart,
            periodStart.plusMonths(1),
            null);
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
