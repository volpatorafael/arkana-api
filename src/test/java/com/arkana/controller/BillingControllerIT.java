package com.arkana.controller;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingPlanPrice;
import com.arkana.domain.BillingPromotionCampaign;
import com.arkana.domain.BillingPromotionCampaignPrice;
import com.arkana.domain.BillingPromotionEligibilityStatus;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderPlanMapping;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.BillingProviderSubscriptionStatus;
import com.arkana.domain.Profile;
import com.arkana.integration.PaymentProvider;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingCheckoutRepository;
import com.arkana.repository.BillingPlanPriceRepository;
import com.arkana.repository.BillingPromotionCampaignPriceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties =
    "spring.datasource.url=jdbc:h2:mem:arkana-billing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class BillingControllerIT extends BaseControllerIT {
    private static final UUID MONTHLY_PLAN_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID YEARLY_PLAN_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000002");

    @Autowired
    private BillingAccountRepository accountRepository;
    @Autowired
    private BillingCheckoutRepository checkoutRepository;
    @Autowired
    private BillingPlanPriceRepository planRepository;
    @Autowired
    private BillingPromotionCampaignPriceRepository campaignPriceRepository;

    @Test
    void shouldStartTrialOnlyForAuthenticatedUser() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        BillingAccount secondAccount = billingAccount(secondUser, BillingAccountStatus.PENDING_PAYMENT);

        mockMvcPerform(post("/v1/billing/trial").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TRIALING"))
            .andExpect(jsonPath("$.accessStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.trialStartedAt").isNotEmpty())
            .andExpect(jsonPath("$.trialEndsAt").isNotEmpty())
            .andExpect(jsonPath("$.currentPeriodStart").isEmpty())
            .andExpect(jsonPath("$.currentPeriodEnd").isEmpty())
            .andExpect(jsonPath("$.cancelAtPeriodEnd").value(false))
            .andExpect(jsonPath("$.currentPlan").isEmpty())
            .andExpect(jsonPath("$.scheduledPlan").isEmpty())
            .andExpect(jsonPath("$.pendingPlan").isEmpty())
            .andExpect(jsonPath("$.nextChargeAt").isEmpty())
            .andExpect(jsonPath("$.overrideEndsAt").isEmpty())
            .andExpect(jsonPath("$.availablePaymentMethods[0]").value("PIX_AUTOMATIC"))
            .andExpect(jsonPath("$.availablePaymentMethods[1]").value("CARD"))
            .andExpect(jsonPath("$.canCheckout").value(false))
            .andExpect(jsonPath("$.canCancel").value(false))
            .andExpect(jsonPath("$.canChangePlan").value(false))
            .andExpect(jsonPath("$.promotion").isEmpty());

        BillingAccount firstAccount = accountRepository.findByOwnerId(firstUser.getId()).orElseThrow();
        BillingAccount unchangedSecond = accountRepository.findByOwnerId(secondUser.getId()).orElseThrow();
        assertThat(firstAccount.getStatus()).isEqualTo(BillingAccountStatus.TRIALING);
        assertThat(unchangedSecond.getId()).isEqualTo(secondAccount.getId());
        assertThat(unchangedSecond.getStatus()).isEqualTo(BillingAccountStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldListOnlyPlansEligibleForAuthenticatedBillingAccount() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        BillingAccount firstAccount = billingAccount(firstUser, BillingAccountStatus.PENDING_PAYMENT);
        BillingAccount secondAccount = billingAccount(secondUser, BillingAccountStatus.PENDING_PAYMENT);
        BillingPromotionCampaign campaign = entityGeneratorService.randomCampaign();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        campaign.setStatus("ACTIVE");
        campaign.setStartsAt(now.minusDays(1));
        campaign.setEndsAt(now.plusDays(30));
        campaign.setRetentionPolicy("WHILE_SUBSCRIPTION_ACTIVE");
        var eligibility = entityGeneratorService.randomEligibility(secondAccount, campaign);
        eligibility.setStatus(BillingPromotionEligibilityStatus.ELIGIBLE);
        eligibility.setGrantedAt(now);
        eligibility.setFirstCheckoutEndsAt(now.plusDays(2));
        eligibility.setLockedAt(null);
        eligibility.setExpiredAt(null);
        eligibility.setForfeitedAt(null);
        accountRepository.flush();

        UUID promotionalPlanId = UUID.randomUUID();
        String promotionalCode = "PRIVATE_" + promotionalPlanId;
        planRepository.saveAndFlush(BillingPlanPrice.builder()
            .id(promotionalPlanId)
            .code(promotionalCode)
            .name("Private promotional plan")
            .billingInterval("MONTH")
            .amount(2900)
            .compareAtAmount(4900)
            .currency("BRL")
            .trialDays(14)
            .availablePaymentMethods(List.of("PIX_AUTOMATIC", "CARD"))
            .active(true)
            .defaultPlan(false)
            .build());
        campaignPriceRepository.saveAndFlush(new BillingPromotionCampaignPrice(
            campaign.getId(), "MONTH", promotionalPlanId, MONTHLY_PLAN_ID));

        mockMvcPerform(get("/v1/billing/plans").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code", not(hasItem(promotionalCode))));

        mockMvcPerform(get("/v1/billing/plans").with(authenticatedAs(secondUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].code", hasItem(promotionalCode)))
            .andExpect(jsonPath("$[0].promotion.code").value(campaign.getCode()))
            .andExpect(jsonPath("$[0].promotion.name").value(campaign.getName()))
            .andExpect(jsonPath("$[0].promotion.campaignEndsAt").isNotEmpty())
            .andExpect(jsonPath("$[0].promotion.offerEndsAt").isNotEmpty())
            .andExpect(jsonPath("$[0].promotion.retentionPolicy").value("WHILE_SUBSCRIPTION_ACTIVE"));

        mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(secondUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promotion.code").value(campaign.getCode()))
            .andExpect(jsonPath("$.promotion.name").value(campaign.getName()))
            .andExpect(jsonPath("$.promotion.status").value("ELIGIBLE"))
            .andExpect(jsonPath("$.promotion.campaignEndsAt").isNotEmpty())
            .andExpect(jsonPath("$.promotion.firstCheckoutEndsAt").isNotEmpty())
            .andExpect(jsonPath("$.promotion.lockedAt").isEmpty());

        assertThat(accountRepository.findById(firstAccount.getId())).isPresent();
    }

    @Test
    void shouldReturnOnlyAuthenticatedUsersSubscription() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        BillingAccount firstAccount = activeBillingAccount(firstUser);
        BillingAccount secondAccount = activeBillingAccount(secondUser);
        firstAccount.setCurrentPlanPriceId(MONTHLY_PLAN_ID);
        secondAccount.setCurrentPlanPriceId(YEARLY_PLAN_ID);
        accountRepository.flush();

        mockMvcPerform(get("/v1/billing/subscription").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.currentPlan.id").value(MONTHLY_PLAN_ID.toString()))
            .andExpect(jsonPath("$.currentPlan.code").value("ARKANA_MONTHLY"))
            .andExpect(jsonPath("$.currentPlan.name").value("Arkana Mensal"))
            .andExpect(jsonPath("$.currentPlan.interval").value("MONTH"))
            .andExpect(jsonPath("$.currentPlan.amount").value(4900))
            .andExpect(jsonPath("$.currentPlan.currency").value("BRL"))
            .andExpect(jsonPath("$.currentPlan.id").value(not(YEARLY_PLAN_ID.toString())));
    }

    @Test
    void shouldScopeCheckoutIdempotencyToAuthenticatedBillingAccount() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        BillingAccount firstAccount = billingAccount(firstUser, BillingAccountStatus.EXPIRED);
        BillingAccount secondAccount = billingAccount(secondUser, BillingAccountStatus.EXPIRED);
        BillingProviderPlanMapping mapping = planMapping(MONTHLY_PLAN_ID);
        UUID sharedKey = UUID.randomUUID();
        BillingCheckout secondCheckout =
            entityGeneratorService.randomBillingCheckout(secondAccount, MONTHLY_PLAN_ID, sharedKey);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30);
        when(abacatePayProvider.createCheckout(argThat(command ->
            command.accountId().equals(firstAccount.getId().toString())
                && command.plan().providerProductId().equals(mapping.getProviderProductId())
                && command.paymentMethod() == BillingPaymentMethod.CARD)))
            .thenReturn(new PaymentProvider.Checkout("provider-first", "https://checkout.test/first", expiresAt));

        mockMvcPerform(post("/v1/billing/checkouts")
                .with(authenticatedAs(firstUser))
                .header("Idempotency-Key", sharedKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planPriceId\":\"" + MONTHLY_PLAN_ID + "\",\"paymentMethod\":\"CARD\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(not(secondCheckout.getId().toString())))
            .andExpect(jsonPath("$.url").value("https://checkout.test/first"))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        BillingCheckout firstCheckout = checkoutRepository
            .findByBillingAccountIdAndIdempotencyKey(firstAccount.getId(), sharedKey)
            .orElseThrow();
        assertThat(firstCheckout.getId()).isNotEqualTo(secondCheckout.getId());
        assertThat(checkoutRepository
            .findByBillingAccountIdAndIdempotencyKey(secondAccount.getId(), sharedKey)
            .orElseThrow()
            .getId()).isEqualTo(secondCheckout.getId());
    }

    @Test
    void shouldCancelOnlyAuthenticatedUsersSubscription() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        BillingAccount firstAccount = activeBillingAccount(firstUser);
        BillingAccount secondAccount = activeBillingAccount(secondUser);
        BillingProviderSubscription firstSubscription = subscription(firstAccount);
        BillingProviderSubscription secondSubscription = subscription(secondAccount);

        mockMvcPerform(post("/v1/billing/subscription/cancel")
                .with(authenticatedAs(firstUser))
                .header("Idempotency-Key", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCEL_AT_PERIOD_END"));

        assertThat(accountRepository.findById(firstAccount.getId()).orElseThrow().isCancelAtPeriodEnd()).isTrue();
        assertThat(accountRepository.findById(secondAccount.getId()).orElseThrow().getStatus())
            .isEqualTo(BillingAccountStatus.ACTIVE);
        verify(abacatePayProvider).cancel(firstSubscription.getProviderSubscriptionId());
        verify(abacatePayProvider, never()).cancel(secondSubscription.getProviderSubscriptionId());
    }

    @Test
    void shouldChangeOnlyAuthenticatedUsersSubscriptionPlan() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        BillingAccount firstAccount = activeBillingAccount(firstUser);
        BillingAccount secondAccount = activeBillingAccount(secondUser);
        BillingProviderSubscription firstSubscription = subscription(firstAccount);
        BillingProviderSubscription secondSubscription = subscription(secondAccount);
        BillingProviderPlanMapping mapping = planMapping(YEARLY_PLAN_ID);

        mockMvcPerform(post("/v1/billing/subscription/change-plan")
                .with(authenticatedAs(firstUser))
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"planPriceId\":\"" + YEARLY_PLAN_ID + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingPlan.id").value(YEARLY_PLAN_ID.toString()));

        assertThat(accountRepository.findById(firstAccount.getId()).orElseThrow().getPendingPlanPriceId())
            .isEqualTo(YEARLY_PLAN_ID);
        assertThat(accountRepository.findById(secondAccount.getId()).orElseThrow().getPendingPlanPriceId()).isNull();
        verify(abacatePayProvider).changePlan(argThat(command ->
            command.subscriptionId().equals(firstSubscription.getProviderSubscriptionId())
                && command.plan().providerProductId().equals(mapping.getProviderProductId())));
        verify(abacatePayProvider, never()).changePlan(argThat(command ->
            command.subscriptionId().equals(secondSubscription.getProviderSubscriptionId())));
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

    private BillingAccount activeBillingAccount(Profile owner) {
        BillingAccount account = billingAccount(owner, BillingAccountStatus.ACTIVE);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        account.setCurrentPeriodStart(now.minusDays(1));
        account.setCurrentPeriodEnd(now.plusMonths(1));
        accountRepository.flush();
        return account;
    }

    private BillingProviderSubscription subscription(BillingAccount account) {
        BillingProviderSubscription subscription = entityGeneratorService.randomSubscription(account, MONTHLY_PLAN_ID);
        subscription.setProvider(BillingProvider.ABACATEPAY);
        subscription.setStatus(BillingProviderSubscriptionStatus.ACTIVE);
        accountRepository.flush();
        return subscription;
    }

    private BillingProviderPlanMapping planMapping(UUID planPriceId) {
        BillingProviderPlanMapping mapping = entityGeneratorService.randomPlanMapping(planPriceId);
        mapping.setProvider(BillingProvider.ABACATEPAY);
        accountRepository.flush();
        return mapping;
    }
}
