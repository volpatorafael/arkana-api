package com.arkana.controller;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.Profile;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingCheckoutRepository;
import com.arkana.repository.BillingProviderSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AsaasBillingFlowIT extends BaseControllerIT {
  private static final String ASAAS_PROPERTY_SOURCE = "asaas-billing-flow";
  private static final UUID MONTHLY_PLAN_ID =
      UUID.fromString("30000000-0000-0000-0000-000000000001");

  @Autowired
  private BillingAccountRepository accountRepository;
  @Autowired
  private BillingCheckoutRepository checkoutRepository;
  @Autowired
  private BillingProviderSubscriptionRepository subscriptionRepository;
  @Autowired
  private ConfigurableEnvironment environment;

  @BeforeEach
  void selectAsaas() {
    environment.getPropertySources().addFirst(new MapPropertySource(
        ASAAS_PROPERTY_SOURCE,
        Map.of("arkana.billing.provider", "ASAAS")));
  }

  @AfterEach
  void restoreProviderSelection() {
    environment.getPropertySources().remove(ASAAS_PROPERTY_SOURCE);
  }

  @Test
  void shouldCreateCardCheckoutRejectPixAndActivateAsaasSubscription() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    BillingAccount account = expiredAccount(user);
    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
    when(asaasProvider.createCheckout(any(PaymentProvider.CreateCheckout.class)))
        .thenReturn(new PaymentProvider.Checkout(
            "chk_asaas",
            "https://sandbox.asaas.com/checkout/chk_asaas",
            expiresAt));

    mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availablePaymentMethods.length()").value(1))
        .andExpect(jsonPath("$.availablePaymentMethods[0]").value("CARD"));

    mockMvcPerform(post("/v1/billing/checkouts")
            .with(authenticatedAs(user))
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planPriceId\":\"" + MONTHLY_PLAN_ID
                + "\",\"paymentMethod\":\"PIX_AUTOMATIC\"}"))
        .andExpect(status().isBadRequest());
    verify(asaasProvider, never()).createCheckout(argThat(command ->
        command.paymentMethod() == BillingPaymentMethod.PIX_AUTOMATIC));

    mockMvcPerform(post("/v1/billing/checkouts")
            .with(authenticatedAs(user))
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planPriceId\":\"" + MONTHLY_PLAN_ID
                + "\",\"paymentMethod\":\"CARD\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.url").value("https://sandbox.asaas.com/checkout/chk_asaas"));

    UUID checkoutId = checkoutRepository.findAll().stream()
        .filter(checkout -> checkout.getBillingAccountId().equals(account.getId()))
        .findFirst()
        .orElseThrow()
        .getId();
    verify(asaasProvider).createCheckout(argThat(command ->
        command.checkoutId().equals(checkoutId.toString())
            && command.plan().providerProductId() == null
            && command.paymentMethod() == BillingPaymentMethod.CARD));

    String signature = "asaas-token";
    OffsetDateTime periodStart = OffsetDateTime.now(ZoneOffset.UTC);
    when(asaasProvider.verifyWebhook(any(byte[].class), eq(signature))).thenReturn(new PaymentWebhookEvent(
        "evt_asaas",
        com.arkana.domain.BillingProviderEventType.COMPLETED,
        "{}",
        "sub_asaas",
        null,
        checkoutId.toString(),
        "chk_asaas",
        periodStart,
        periodStart.plusMonths(1),
        null));
    mockMvcPerform(post("/v1/webhook/payment/asaas")
            .header("asaas-access-token", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isOk());

    BillingAccount activated = accountRepository.findById(account.getId()).orElseThrow();
    assertThat(activated.getStatus()).isEqualTo(BillingAccountStatus.ACTIVE);
    assertThat(activated.getCurrentProvider()).isEqualTo(BillingProvider.ASAAS);
    assertThat(subscriptionRepository.findByBillingAccountIdAndProvider(
        account.getId(),
        BillingProvider.ASAAS)).isPresent();
  }

  @Test
  void shouldKeepExistingSubscriptionOnItsOriginalProvider() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    BillingAccount account = entityGeneratorService.randomBillingAccount(user);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    account.setStatus(BillingAccountStatus.ACTIVE);
    account.setCurrentProvider(BillingProvider.ABACATEPAY);
    account.setCurrentPlanPriceId(MONTHLY_PLAN_ID);
    account.setCurrentPeriodStart(now.minusDays(1));
    account.setCurrentPeriodEnd(now.plusMonths(1));
    BillingProviderSubscription subscription = entityGeneratorService.randomSubscription(account);
    subscription.setProvider(BillingProvider.ABACATEPAY);
    subscriptionRepository.flush();

    mockMvcPerform(post("/v1/billing/subscription/cancel")
            .with(authenticatedAs(user))
            .header("Idempotency-Key", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCEL_AT_PERIOD_END"));

    verify(abacatePayProvider).cancel(subscription.getProviderSubscriptionId());
    verify(asaasProvider, never()).cancel(any(String.class));
  }

  private BillingAccount expiredAccount(Profile user) {
    BillingAccount account = entityGeneratorService.randomBillingAccount(user);
    account.setStatus(BillingAccountStatus.EXPIRED);
    account.setTrialStartedAt(null);
    account.setTrialEndsAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    account.setCurrentPeriodStart(null);
    account.setCurrentPeriodEnd(null);
    account.setCurrentPlanPriceId(null);
    account.setPendingPlanPriceId(null);
    account.setCurrentProvider(null);
    account.setCancelAtPeriodEnd(false);
    return accountRepository.saveAndFlush(account);
  }
}
