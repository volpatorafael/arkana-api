package com.arkana.controller;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.BillingProviderSubscriptionStatus;
import com.arkana.domain.BillingCheckoutStatus;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AsaasBillingFlowIT extends BaseControllerIT {
  private static final String ASAAS_PROPERTY_SOURCE = "asaas-billing-flow";
  private static final UUID MONTHLY_PLAN_ID =
      UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID YEARLY_PLAN_ID =
      UUID.fromString("30000000-0000-0000-0000-000000000002");

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
  void shouldScheduleCardDuringTrialAndActivateOnlyAfterPayment() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    mockMvcPerform(post("/v1/billing/trial").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.canCheckout").value(true));
    BillingAccount account = accountRepository.findByOwnerId(user.getId()).orElseThrow();
    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
    when(asaasProvider.createCheckout(any(PaymentProvider.CreateCheckout.class)))
        .thenReturn(new PaymentProvider.Checkout(
            "chk_asaas",
            "https://sandbox.asaas.com/checkout/chk_asaas",
            expiresAt));

    mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availablePaymentMethods.length()").value(1))
        .andExpect(jsonPath("$.availablePaymentMethods[0]").value("CARD"))
        .andExpect(jsonPath("$.scheduledPlan").isEmpty())
        .andExpect(jsonPath("$.nextChargeAt").isEmpty());

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
            .content(checkoutJson(MONTHLY_PLAN_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.action.url").value("https://sandbox.asaas.com/checkout/chk_asaas"));

    UUID checkoutId = checkoutRepository.findAll().stream()
        .filter(checkout -> checkout.getBillingAccountId().equals(account.getId()))
        .findFirst()
        .orElseThrow()
        .getId();
    verify(asaasProvider).createCheckout(argThat(command ->
        command.checkoutId().equals(checkoutId.toString())
            && command.plan().providerProductId() == null
            && command.paymentMethod() == BillingPaymentMethod.CARD
            && command.firstChargeAt().equals(account.getTrialEndsAt())));

    String signature = "asaas-token";
    when(asaasProvider.verifyWebhook(any(byte[].class), eq(signature))).thenReturn(new PaymentWebhookEvent(
        "evt_asaas_linked",
        com.arkana.domain.BillingProviderEventType.SUBSCRIPTION_LINKED,
        "{}",
        "sub_asaas",
        null,
        checkoutId.toString(),
        "chk_asaas",
        null,
        null,
        null));
    mockMvcPerform(post("/v1/webhook/payment/asaas")
            .header("asaas-access-token", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isOk());

    mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.accessStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.currentPlan").isEmpty())
        .andExpect(jsonPath("$.scheduledPlan.id").value(MONTHLY_PLAN_ID.toString()))
        .andExpect(jsonPath("$.nextChargeAt").isNotEmpty())
        .andExpect(jsonPath("$.canCheckout").value(false))
        .andExpect(jsonPath("$.canCancel").value(true))
        .andExpect(jsonPath("$.canChangePlan").value(true));
    assertThat(checkoutRepository.findById(checkoutId).orElseThrow().getStatus())
        .isEqualTo(BillingCheckoutStatus.SCHEDULED);
    BillingProviderSubscription scheduled = subscriptionRepository
        .findByBillingAccountIdAndProvider(account.getId(), BillingProvider.ASAAS)
        .orElseThrow();
    assertThat(scheduled.getStatus()).isEqualTo(BillingProviderSubscriptionStatus.SCHEDULED);
    assertThat(scheduled.getPlanPriceId()).isEqualTo(MONTHLY_PLAN_ID);
    assertThat(scheduled.getNextChargeAt()).isEqualTo(account.getTrialEndsAt());

    OffsetDateTime periodStart = OffsetDateTime.now(ZoneOffset.UTC);
    when(asaasProvider.verifyWebhook(any(byte[].class), eq(signature))).thenReturn(new PaymentWebhookEvent(
        "evt_asaas_paid",
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
    assertThat(checkoutRepository.findById(checkoutId).orElseThrow().getStatus())
        .isEqualTo(BillingCheckoutStatus.COMPLETED);
    assertThat(subscriptionRepository.findByBillingAccountIdAndProvider(
        account.getId(), BillingProvider.ASAAS).orElseThrow().getStatus())
        .isEqualTo(BillingProviderSubscriptionStatus.ACTIVE);
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
    BillingProviderSubscription subscription = entityGeneratorService.randomSubscription(account, MONTHLY_PLAN_ID);
    subscription.setProvider(BillingProvider.ABACATEPAY);
    subscription.setStatus(BillingProviderSubscriptionStatus.ACTIVE);
    subscriptionRepository.flush();

    mockMvcPerform(post("/v1/billing/subscription/cancel")
            .with(authenticatedAs(user))
            .header("Idempotency-Key", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCEL_AT_PERIOD_END"));

    verify(abacatePayProvider).cancel(subscription.getProviderSubscriptionId());
    verify(asaasProvider, never()).cancel(any(String.class));
  }

  @Test
  void shouldReuseOpenCheckoutAndRejectDifferentPlan() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    mockMvcPerform(post("/v1/billing/trial").with(authenticatedAs(user)))
        .andExpect(status().isOk());
    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
    when(asaasProvider.createCheckout(any(PaymentProvider.CreateCheckout.class)))
        .thenReturn(new PaymentProvider.Checkout(
            "chk_open", "https://sandbox.asaas.com/checkout/chk_open", expiresAt));

    createCheckout(user, MONTHLY_PLAN_ID, UUID.randomUUID())
        .andExpect(status().isCreated());
    createCheckout(user, MONTHLY_PLAN_ID, UUID.randomUUID())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.action.url").value("https://sandbox.asaas.com/checkout/chk_open"));
    createCheckout(user, YEARLY_PLAN_ID, UUID.randomUUID())
        .andExpect(status().isConflict());

    verify(asaasProvider, times(1)).createCheckout(any(PaymentProvider.CreateCheckout.class));
  }

  @Test
  void shouldChargeImmediatelyWhenTrialAlreadyExpired() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    expiredAccount(user);
    OffsetDateTime beforeCheckout = OffsetDateTime.now(ZoneOffset.UTC);
    when(asaasProvider.createCheckout(any(PaymentProvider.CreateCheckout.class)))
        .thenReturn(new PaymentProvider.Checkout(
            "chk_immediate",
            "https://sandbox.asaas.com/checkout/chk_immediate",
            beforeCheckout.plusMinutes(30)));

    createCheckout(user, MONTHLY_PLAN_ID, UUID.randomUUID())
        .andExpect(status().isCreated());

    OffsetDateTime afterCheckout = OffsetDateTime.now(ZoneOffset.UTC);
    verify(asaasProvider).createCheckout(argThat(command ->
        !command.firstChargeAt().isBefore(beforeCheckout)
            && !command.firstChargeAt().isAfter(afterCheckout)));
  }

  @Test
  void shouldChangeAndCancelScheduledPlanWithoutEndingTrial() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    mockMvcPerform(post("/v1/billing/trial").with(authenticatedAs(user)))
        .andExpect(status().isOk());
    BillingAccount account = accountRepository.findByOwnerId(user.getId()).orElseThrow();
    OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
    when(asaasProvider.createCheckout(any(PaymentProvider.CreateCheckout.class)))
        .thenReturn(new PaymentProvider.Checkout(
            "chk_scheduled", "https://sandbox.asaas.com/checkout/chk_scheduled", expiresAt));
    createCheckout(user, MONTHLY_PLAN_ID, UUID.randomUUID()).andExpect(status().isCreated());
    UUID checkoutId = checkoutRepository.findAll().stream()
        .filter(checkout -> checkout.getBillingAccountId().equals(account.getId()))
        .findFirst()
        .orElseThrow()
        .getId();
    String signature = "asaas-token";
    sendWebhook(signature, new PaymentWebhookEvent(
        "evt_schedule_change",
        com.arkana.domain.BillingProviderEventType.SUBSCRIPTION_LINKED,
        "{}",
        "sub_scheduled",
        null,
        checkoutId.toString(),
        "chk_scheduled",
        null,
        null,
        null));

    mockMvcPerform(post("/v1/billing/subscription/change-plan")
            .with(authenticatedAs(user))
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"planPriceId\":\"" + YEARLY_PLAN_ID + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.scheduledPlan.id").value(MONTHLY_PLAN_ID.toString()))
        .andExpect(jsonPath("$.pendingPlan.id").value(YEARLY_PLAN_ID.toString()));
    verify(asaasProvider).changePlan(argThat(command ->
        command.subscriptionId().equals("sub_scheduled")
            && command.plan().id().equals(YEARLY_PLAN_ID.toString())
            && command.nextChargeAt().equals(account.getTrialEndsAt())
            && command.updatePendingPayments()));

    sendWebhook(signature, new PaymentWebhookEvent(
        "evt_schedule_changed",
        com.arkana.domain.BillingProviderEventType.PLAN_CHANGED,
        "{}",
        "sub_scheduled",
        null,
        null,
        null,
        null,
        null,
        null));
    mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.scheduledPlan.id").value(YEARLY_PLAN_ID.toString()))
        .andExpect(jsonPath("$.pendingPlan").isEmpty());

    mockMvcPerform(post("/v1/billing/subscription/cancel")
            .with(authenticatedAs(user))
            .header("Idempotency-Key", UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.accessStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.scheduledPlan").isEmpty())
        .andExpect(jsonPath("$.nextChargeAt").isEmpty())
        .andExpect(jsonPath("$.canCheckout").value(true));
    verify(asaasProvider).cancel("sub_scheduled", BillingPaymentMethod.CARD);
    assertThat(subscriptionRepository.findByBillingAccountIdAndProvider(
        account.getId(), BillingProvider.ASAAS).orElseThrow().getStatus())
        .isEqualTo(BillingProviderSubscriptionStatus.CANCELED);

    sendWebhook(signature, new PaymentWebhookEvent(
        "evt_delayed_cancel",
        com.arkana.domain.BillingProviderEventType.CANCELED,
        "{}",
        "sub_scheduled",
        null,
        null,
        null,
        null,
        null,
        null));
    mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.accessStatus").value("ACTIVE"));
  }

  @Test
  void shouldBlockAfterScheduledFirstPaymentFailsWithoutChangingAnotherAccount() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    Profile otherUser = entityGeneratorService.randomProfile();
    mockMvcPerform(post("/v1/billing/trial").with(authenticatedAs(user)))
        .andExpect(status().isOk());
    BillingAccount account = accountRepository.findByOwnerId(user.getId()).orElseThrow();
    BillingAccount otherAccount = expiredAccount(otherUser);
    account.setCurrentProvider(BillingProvider.ASAAS);
    accountRepository.flush();
    com.arkana.domain.BillingCheckout checkout = entityGeneratorService.randomBillingCheckout(
        account, MONTHLY_PLAN_ID, UUID.randomUUID());
    checkout.setProvider(BillingProvider.ASAAS);
    checkout.setStatus(BillingCheckoutStatus.SCHEDULED);
    checkoutRepository.flush();
    BillingProviderSubscription subscription = entityGeneratorService.randomSubscription(
        account, MONTHLY_PLAN_ID);
    subscription.setProvider(BillingProvider.ASAAS);
    subscription.setStatus(BillingProviderSubscriptionStatus.SCHEDULED);
    subscription.setNextChargeAt(account.getTrialEndsAt());
    subscriptionRepository.flush();

    sendWebhook("asaas-token", new PaymentWebhookEvent(
        "evt_first_payment_failed",
        com.arkana.domain.BillingProviderEventType.PAYMENT_FAILED,
        "{}",
        subscription.getProviderSubscriptionId(),
        null,
        checkout.getId().toString(),
        checkout.getProviderCheckoutId(),
        account.getTrialEndsAt(),
        null,
        null));

    mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAST_DUE"))
        .andExpect(jsonPath("$.accessStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.canCheckout").value(false));
    account.setTrialEndsAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    accountRepository.flush();
    mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAST_DUE"))
        .andExpect(jsonPath("$.accessStatus").value("BLOCKED"))
        .andExpect(jsonPath("$.canCheckout").value(false));
    assertThat(accountRepository.findById(otherAccount.getId()).orElseThrow().getStatus())
        .isEqualTo(BillingAccountStatus.EXPIRED);
  }

  private org.springframework.test.web.servlet.ResultActions createCheckout(
      Profile user,
      UUID planId,
      UUID idempotencyKey) throws Exception {
    return mockMvcPerform(post("/v1/billing/checkouts")
        .with(authenticatedAs(user))
        .header("Idempotency-Key", idempotencyKey)
        .contentType(MediaType.APPLICATION_JSON)
        .content(checkoutJson(planId)));
  }

  private String checkoutJson(UUID planId) {
    return "{\"planPriceId\":\"" + planId + "\",\"paymentMethod\":\"CARD\","
        + "\"paymentToken\":\"test-token\",\"payer\":{\"name\":\"Maria da Silva\","
        + "\"document\":\"52998224725\",\"phoneNumber\":\"11999999999\","
        + "\"billingAddress\":{\"street\":\"Rua Um\",\"number\":\"10\","
        + "\"neighborhood\":\"Centro\",\"zipCode\":\"01001000\","
        + "\"city\":\"Sao Paulo\",\"state\":\"SP\"}}}";
  }

  private void sendWebhook(String signature, PaymentWebhookEvent event) throws Exception {
    when(asaasProvider.verifyWebhook(any(byte[].class), eq(signature))).thenReturn(event);
    mockMvcPerform(post("/v1/webhook/payment/asaas")
            .header("asaas-access-token", signature)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isOk());
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
