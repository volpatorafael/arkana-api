package com.arkana.controller;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingCheckoutStatus;
import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderEvent;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.domain.BillingProviderPlanMapping;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.Profile;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingCheckoutRepository;
import com.arkana.repository.BillingProviderEventRepository;
import com.arkana.repository.BillingProviderPlanMappingRepository;
import com.arkana.repository.BillingProviderSubscriptionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingFlowIT extends BaseControllerIT {
    private static final UUID MONTHLY_PLAN_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID YEARLY_PLAN_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final String MONTHLY_PRODUCT_ID = "abacate-monthly";
    private static final String YEARLY_PRODUCT_ID = "abacate-yearly";

    @Autowired
    private BillingAccountRepository accountRepository;
    @Autowired
    private BillingCheckoutRepository checkoutRepository;
    @Autowired
    private BillingProviderSubscriptionRepository subscriptionRepository;
    @Autowired
    private BillingProviderEventRepository eventRepository;
    @Autowired
    private BillingProviderPlanMappingRepository planMappingRepository;

    @Test
    void shouldCompleteTrialCheckoutAndSubscriptionActivationFlow() throws Exception {
        Profile user = entityGeneratorService.randomProfile();
        BillingProviderPlanMapping monthlyMapping = planMapping(MONTHLY_PLAN_ID, MONTHLY_PRODUCT_ID);

        mockMvcPerform(post("/v1/billing/trial").with(authenticatedAs(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TRIALING"))
            .andExpect(jsonPath("$.accessStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.canCheckout").value(false));

        BillingAccount account = accountRepository.findByOwnerId(user.getId()).orElseThrow();
        account.setTrialEndsAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        accountRepository.flush();

        mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("EXPIRED"))
            .andExpect(jsonPath("$.accessStatus").value("BLOCKED"))
            .andExpect(jsonPath("$.canCheckout").value(true));

        UUID idempotencyKey = UUID.randomUUID();
        OffsetDateTime checkoutExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
        when(abacatePayProvider.createCheckout(argThat(command ->
            command.accountId().equals(account.getId().toString())
                && command.plan().providerProductId().equals(monthlyMapping.getProviderProductId())
                && command.paymentMethod() == BillingPaymentMethod.CARD)))
            .thenReturn(new PaymentProvider.Checkout(
                "provider-checkout-monthly",
                "https://checkout.arkana.test/monthly",
                checkoutExpiresAt));

        MvcResult firstCheckout = createCheckout(user, idempotencyKey)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.action.url").value("https://checkout.arkana.test/monthly"))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andReturn();
        String checkoutId = JsonPath.read(firstCheckout.getResponse().getContentAsString(), "$.id");

        createCheckout(user, idempotencyKey)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(checkoutId))
            .andExpect(jsonPath("$.action.url").value("https://checkout.arkana.test/monthly"));

        verify(abacatePayProvider, times(1)).createCheckout(argThat(command ->
            command.accountId().equals(account.getId().toString())
                && command.checkoutId().equals(checkoutId)
                && command.plan().providerProductId().equals(MONTHLY_PRODUCT_ID)
                && command.paymentMethod() == BillingPaymentMethod.CARD));
        assertThat(checkoutRepository.findAll().stream()
            .filter(checkout -> checkout.getBillingAccountId().equals(account.getId())))
            .singleElement()
            .satisfies(checkout -> {
                assertThat(checkout.getStatus()).isEqualTo(BillingCheckoutStatus.PENDING);
                assertThat(checkout.getPlanPriceId()).isEqualTo(MONTHLY_PLAN_ID);
                assertThat(checkout.getPaymentMethod()).isEqualTo(BillingPaymentMethod.CARD);
                assertThat(checkout.getProviderCheckoutId()).isEqualTo("provider-checkout-monthly");
            });

        OffsetDateTime periodStart = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        OffsetDateTime periodEnd = periodStart.plusMonths(1);
        postProviderEvent(providerEvent(
            "completed-event",
            BillingProviderEventType.COMPLETED,
            "provider-subscription",
            null,
            checkoutId,
            periodStart,
            periodEnd));

        mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.accessStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.currentPlan.id").value(MONTHLY_PLAN_ID.toString()))
            .andExpect(jsonPath("$.pendingPlan").isEmpty())
            .andExpect(jsonPath("$.currentPeriodStart").isNotEmpty())
            .andExpect(jsonPath("$.currentPeriodEnd").isNotEmpty())
            .andExpect(jsonPath("$.canCheckout").value(false))
            .andExpect(jsonPath("$.canCancel").value(true))
            .andExpect(jsonPath("$.canChangePlan").value(true));

        BillingAccount persistedAccount = accountRepository.findById(account.getId()).orElseThrow();
        BillingCheckout persistedCheckout = checkoutRepository.findById(UUID.fromString(checkoutId)).orElseThrow();
        BillingProviderSubscription subscription = subscriptionRepository
            .findByBillingAccountIdAndProvider(account.getId(), BillingProvider.ABACATEPAY)
            .orElseThrow();
        assertThat(persistedAccount.getStatus()).isEqualTo(BillingAccountStatus.ACTIVE);
        assertThat(persistedAccount.getCurrentPlanPriceId()).isEqualTo(MONTHLY_PLAN_ID);
        assertThat(persistedAccount.getCurrentPeriodStart()).isEqualTo(periodStart);
        assertThat(persistedAccount.getCurrentPeriodEnd()).isEqualTo(periodEnd);
        assertThat(persistedCheckout.getStatus()).isEqualTo(BillingCheckoutStatus.COMPLETED);
        assertThat(subscription.getProviderSubscriptionId()).isEqualTo("provider-subscription");
        assertProcessed("completed-event");
    }

    @Test
    void shouldRecoverSubscriptionAfterPaymentFailure() throws Exception {
        Profile user = entityGeneratorService.randomProfile();
        ActiveSubscription active = activateMonthlySubscription(user);
        Profile otherUser = entityGeneratorService.randomProfile();
        BillingAccount otherAccount = activeAccount(otherUser, YEARLY_PLAN_ID);

        postProviderEvent(providerEvent(
            "payment-failed-event",
            BillingProviderEventType.PAYMENT_FAILED,
            active.providerSubscriptionId(),
            null,
            null,
            null,
            null));

        mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAST_DUE"))
            .andExpect(jsonPath("$.accessStatus").value("BLOCKED"));
        assertThat(accountRepository.findById(otherAccount.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingAccountStatus.ACTIVE);
        assertThat(accountRepository.findById(otherAccount.getId()).orElseThrow().getCurrentPlanPriceId())
            .isEqualTo(YEARLY_PLAN_ID);

        OffsetDateTime renewedAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime renewedUntil = renewedAt.plusMonths(1);
        postProviderEvent(providerEvent(
            "renewed-event",
            BillingProviderEventType.RENEWED,
            active.providerSubscriptionId(),
            null,
            null,
            renewedAt,
            renewedUntil));

        mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.accessStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.currentPlan.id").value(MONTHLY_PLAN_ID.toString()))
            .andExpect(jsonPath("$.currentPeriodStart").isNotEmpty())
            .andExpect(jsonPath("$.currentPeriodEnd").isNotEmpty());

        BillingAccount recovered = accountRepository.findById(active.accountId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(BillingAccountStatus.ACTIVE);
        assertThat(recovered.getCurrentPeriodStart()).isEqualTo(renewedAt);
        assertThat(recovered.getCurrentPeriodEnd()).isEqualTo(renewedUntil);
        assertProcessed("payment-failed-event");
        assertProcessed("renewed-event");
    }

    @Test
    void shouldChangePlanAndCancelSubscription() throws Exception {
        Profile user = entityGeneratorService.randomProfile();
        ActiveSubscription active = activateMonthlySubscription(user);
        planMapping(YEARLY_PLAN_ID, YEARLY_PRODUCT_ID);
        clearInvocations(abacatePayProvider);

        mockMvcPerform(post("/v1/billing/subscription/change-plan")
                .with(authenticatedAs(user))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planPriceId\":\"" + YEARLY_PLAN_ID + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentPlan.id").value(MONTHLY_PLAN_ID.toString()))
            .andExpect(jsonPath("$.pendingPlan.id").value(YEARLY_PLAN_ID.toString()));

        verify(abacatePayProvider).changePlan(argThat(command ->
            command.subscriptionId().equals(active.providerSubscriptionId())
                && command.plan().providerProductId().equals(YEARLY_PRODUCT_ID)));
        BillingAccount scheduled = accountRepository.findById(active.accountId()).orElseThrow();
        assertThat(scheduled.getCurrentPlanPriceId()).isEqualTo(MONTHLY_PLAN_ID);
        assertThat(scheduled.getPendingPlanPriceId()).isEqualTo(YEARLY_PLAN_ID);

        OffsetDateTime yearlyPeriodStart = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime yearlyPeriodEnd = yearlyPeriodStart.plusYears(1);
        postProviderEvent(providerEvent(
            "plan-changed-event",
            BillingProviderEventType.PLAN_CHANGED,
            active.providerSubscriptionId(),
            YEARLY_PRODUCT_ID,
            null,
            yearlyPeriodStart,
            yearlyPeriodEnd));

        mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.currentPlan.id").value(YEARLY_PLAN_ID.toString()))
            .andExpect(jsonPath("$.pendingPlan").isEmpty());
        BillingAccount changed = accountRepository.findById(active.accountId()).orElseThrow();
        assertThat(changed.getCurrentPlanPriceId()).isEqualTo(YEARLY_PLAN_ID);
        assertThat(changed.getPendingPlanPriceId()).isNull();

        mockMvcPerform(post("/v1/billing/subscription/cancel")
                .with(authenticatedAs(user))
                .header("Idempotency-Key", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCEL_AT_PERIOD_END"))
            .andExpect(jsonPath("$.accessStatus").value("ACTIVE"));
        verify(abacatePayProvider).cancel(active.providerSubscriptionId(), BillingPaymentMethod.CARD);

        postProviderEvent(providerEvent(
            "cancelled-event",
            BillingProviderEventType.CANCELED,
            active.providerSubscriptionId(),
            null,
            null,
            yearlyPeriodStart,
            yearlyPeriodEnd));

        mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCEL_AT_PERIOD_END"))
            .andExpect(jsonPath("$.accessStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.canCheckout").value(false))
            .andExpect(jsonPath("$.canCancel").value(false))
            .andExpect(jsonPath("$.canChangePlan").value(false));

        BillingAccount canceled = accountRepository.findById(active.accountId()).orElseThrow();
        assertThat(canceled.getStatus()).isEqualTo(BillingAccountStatus.CANCEL_AT_PERIOD_END);
        assertThat(canceled.getCurrentPlanPriceId()).isEqualTo(YEARLY_PLAN_ID);
        assertProcessed("plan-changed-event");
        assertProcessed("cancelled-event");
    }

    private org.springframework.test.web.servlet.ResultActions createCheckout(
        Profile user,
        UUID idempotencyKey) throws Exception {
        return mockMvcPerform(post("/v1/billing/checkouts")
            .with(authenticatedAs(user))
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(checkoutJson(MONTHLY_PLAN_ID)));
    }

    private String checkoutJson(UUID planId) {
        return "{\"planPriceId\":\"" + planId + "\",\"paymentMethod\":\"CARD\","
            + "\"paymentToken\":\"test-token\",\"payer\":{\"name\":\"Maria da Silva\","
            + "\"document\":\"52998224725\",\"phoneNumber\":\"11999999999\","
            + "\"billingAddress\":{\"street\":\"Rua Um\",\"number\":\"10\","
            + "\"neighborhood\":\"Centro\",\"zipCode\":\"01001000\","
            + "\"city\":\"Sao Paulo\",\"state\":\"SP\"}}}";
    }

    private ActiveSubscription activateMonthlySubscription(Profile user) throws Exception {
        BillingProviderPlanMapping mapping = planMapping(MONTHLY_PLAN_ID, MONTHLY_PRODUCT_ID);
        mockMvcPerform(post("/v1/billing/trial").with(authenticatedAs(user)))
            .andExpect(status().isOk());
        BillingAccount account = accountRepository.findByOwnerId(user.getId()).orElseThrow();
        account.setTrialEndsAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        accountRepository.flush();

        UUID idempotencyKey = UUID.randomUUID();
        when(abacatePayProvider.createCheckout(argThat(command ->
            command.accountId().equals(account.getId().toString())
                && command.plan().providerProductId().equals(mapping.getProviderProductId())
                && command.paymentMethod() == BillingPaymentMethod.CARD)))
            .thenReturn(new PaymentProvider.Checkout(
                "provider-checkout-" + account.getId(),
                "https://checkout.arkana.test/" + account.getId(),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30)));
        MvcResult checkoutResult = createCheckout(user, idempotencyKey)
            .andExpect(status().isCreated())
            .andReturn();
        String checkoutId = JsonPath.read(checkoutResult.getResponse().getContentAsString(), "$.id");
        String providerSubscriptionId = "provider-subscription-" + account.getId();
        OffsetDateTime periodStart = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        postProviderEvent(providerEvent(
            "completed-" + account.getId(),
            BillingProviderEventType.COMPLETED,
            providerSubscriptionId,
            null,
            checkoutId,
            periodStart,
            periodStart.plusMonths(1)));
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingAccountStatus.ACTIVE);
        return new ActiveSubscription(account.getId(), providerSubscriptionId);
    }

    private BillingProviderPlanMapping planMapping(UUID planPriceId, String productId) {
        BillingProviderPlanMapping mapping = entityGeneratorService.randomPlanMapping(planPriceId);
        mapping.setProvider(BillingProvider.ABACATEPAY);
        mapping.setProviderProductId(productId);
        return planMappingRepository.saveAndFlush(mapping);
    }

    private BillingAccount activeAccount(Profile owner, UUID planPriceId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BillingAccount account = entityGeneratorService.randomBillingAccount(owner);
        account.setStatus(BillingAccountStatus.ACTIVE);
        account.setTrialStartedAt(null);
        account.setTrialEndsAt(null);
        account.setCurrentPlanPriceId(planPriceId);
        account.setPendingPlanPriceId(null);
        account.setCurrentPeriodStart(now.minusDays(1));
        account.setCurrentPeriodEnd(now.plusMonths(1));
        account.setCancelAtPeriodEnd(false);
        account.setOverrideEndsAt(null);
        return accountRepository.saveAndFlush(account);
    }

    private PaymentWebhookEvent providerEvent(
        String id,
        BillingProviderEventType type,
        String subscriptionId,
        String productId,
        String checkoutId,
        OffsetDateTime periodStart,
        OffsetDateTime periodEnd) {
        return new PaymentWebhookEvent(
            id,
            type,
            "{\"id\":\"" + id + "\"}",
            subscriptionId,
            productId,
            checkoutId,
            periodStart,
            periodEnd,
            null);
    }

    private void postProviderEvent(PaymentWebhookEvent event) throws Exception {
        String eventId = event.id();
        String signature = "signature-" + eventId;
        when(abacatePayProvider.verifyWebhook(any(byte[].class), eq(signature))).thenReturn(event);
        mockMvcPerform(post("/v1/webhook/payment/abacatepay?webhookSecret=test-webhook-secret")
                .header("X-Webhook-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + eventId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(true));
    }

    private void assertProcessed(String eventId) {
        assertThat(eventRepository.findAll().stream()
            .filter(event -> event.getProviderEventId().equals(eventId)))
            .singleElement()
            .extracting(BillingProviderEvent::getProcessingStatus)
            .isEqualTo("PROCESSED");
    }

    private record ActiveSubscription(UUID accountId, String providerSubscriptionId) {
    }
}
