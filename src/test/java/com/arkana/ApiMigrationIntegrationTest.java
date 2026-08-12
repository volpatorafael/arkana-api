package com.arkana;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingCheckout;
import com.arkana.domain.BillingCheckoutStatus;
import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingPlanPrice;
import com.arkana.domain.BillingPromotionCampaign;
import com.arkana.domain.BillingPromotionCampaignPrice;
import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderEventType;
import com.arkana.domain.Profile;
import com.arkana.controller.BaseControllerIT;
import com.arkana.integration.dto.PaymentWebhookEvent;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.BillingCheckoutRepository;
import com.arkana.repository.BillingPlanPriceRepository;
import com.arkana.repository.BillingPromotionCampaignPriceRepository;
import com.arkana.repository.BillingPromotionCampaignRepository;
import com.arkana.repository.BillingProviderEventRepository;
import com.arkana.repository.ProfileRepository;
import com.arkana.service.BillingService;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiMigrationIntegrationTest extends BaseControllerIT {
  private static final UUID USER_ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID USER_TWO = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID USER_THREE = UUID.fromString("20000000-0000-0000-0000-000000000003");
  private static final UUID USER_FOUR = UUID.fromString("20000000-0000-0000-0000-000000000004");
  private static final UUID USER_FIVE = UUID.fromString("20000000-0000-0000-0000-000000000005");

  @Autowired
  MockMvc mvc;
  @Autowired
  ProfileRepository profiles;
  @Autowired
  BillingService billing;
  @Autowired
  BillingAccountRepository billingAccounts;
  @Autowired
  BillingCheckoutRepository billingCheckouts;
  @Autowired
  BillingProviderEventRepository billingProviderEvents;
  @Autowired
  BillingPlanPriceRepository billingPlanPrices;
  @Autowired
  BillingPromotionCampaignRepository billingPromotionCampaigns;
  @Autowired
  BillingPromotionCampaignPriceRepository billingPromotionCampaignPrices;
  @Autowired
  MeterRegistry meterRegistry;
  @Autowired
  CacheManager cacheManager;

  private static org.springframework.test.web.servlet.request.RequestPostProcessor user(UUID id) {
    return jwt().jwt(token -> token
        .subject(id.toString())
        .claim("aud", "authenticated")
        .claim("email", id + "@arkana.test"));
  }

  @BeforeEach
  void setUpProfiles() {
    profiles.save(profile(USER_ONE, "one@arkana.test", "pt-BR"));
    profiles.save(profile(USER_TWO, "two@arkana.test", "en"));
    profiles.flush();
    billing.startTrial(USER_ONE);
    billing.startTrial(USER_TWO);
  }

  @Test
  void faviconIsPublic() throws Exception {
    mvc.perform(get("/favicon.ico"))
        .andExpect(status().isOk());
  }

  @Test
  void authenticationAndProfileUseTheJwtSubject() throws Exception {
    mvc.perform(get("/v1/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value("A valid access token is required."))
        .andExpect(jsonPath("$.instance").value("/v1/profile"))
        .andExpect(jsonPath("$.code").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());

    mvc.perform(get("/v1/profile").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(USER_ONE.toString()))
        .andExpect(jsonPath("$.email").value("one@arkana.test"))
        .andExpect(jsonPath("$.status").doesNotExist());

    mvc.perform(patch("/v1/profile").with(user(USER_ONE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"displayName\":\"Rafael\",\"locale\":\"en\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Rafael"))
        .andExpect(jsonPath("$.locale").value("en"));
  }

  @Test
  void repositoryQueriesPublishMetrics() {
    profiles.findById(USER_ONE);

    double queryCount = meterRegistry.find("arkana_repository_query_count")
        .counters()
        .stream()
        .mapToDouble(counter -> counter.count())
        .sum();
    org.junit.jupiter.api.Assertions.assertTrue(queryCount > 0);
  }

  @Test
  void liquibaseLoadsTheCompleteLocalizedCatalog() throws Exception {
    mvc.perform(get("/v1/cards").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(78)))
        .andExpect(jsonPath("$[0].name", notNullValue()));

    mvc.perform(get("/v1/cards?deckMode=MAJOR&locale=en").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(22)))
        .andExpect(jsonPath("$[0].name").value("The Fool"));

    mvc.perform(get("/v1/spreads").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(12)))
        .andExpect(jsonPath("$[0].positions").isArray());
  }

  @Test
  void billingTrialIsIdempotentAndUnlocksProductAccess() throws Exception {
    mvc.perform(post("/v1/billing/trial").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("TRIALING"))
        .andExpect(jsonPath("$.accessStatus").value("ACTIVE"));
    mvc.perform(get("/v1/billing/subscription").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trialEndsAt").isNotEmpty());
    mvc.perform(get("/v1/billing/plans").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));

    configureFounderCampaign();
    Cache publicPlansCache = cacheManager.getCache("public-billing-plans");
    if (publicPlansCache != null) {
      publicPlansCache.clear();
    }
    mvc.perform(get("/v1/public/plans"))
        .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].promotion.code").value("FOUNDERS"));
    profiles.save(profile(USER_THREE, "three@arkana.test", "pt-BR"));
    profiles.flush();
    mvc.perform(get("/v1/cards").with(user(USER_THREE)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("An active trial or subscription is required."));
    mvc.perform(post("/v1/billing/trial").with(user(USER_THREE)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.accessStatus").value("ACTIVE"));
    mvc.perform(get("/v1/billing/plans").with(user(USER_THREE)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].promotion.code").value("FOUNDERS"));
    mvc.perform(get("/v1/cards?deckMode=MAJOR").with(user(USER_THREE)))
        .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(22)));

    profiles.save(profile(USER_FOUR, "one@arkana.test", "pt-BR"));
    profiles.flush();
    mvc.perform(post("/v1/billing/trial").with(user(USER_FOUR)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("This email has already used a trial."));
  }

  @Test
  void authenticatedReaderCanStartUsingTheProductAfterProfileCreation() throws Exception {
    mvc.perform(get("/v1/profile").with(user(USER_FIVE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(USER_FIVE.toString()))
        .andExpect(jsonPath("$.status").doesNotExist());

    mvc.perform(post("/v1/billing/trial").with(user(USER_FIVE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessStatus").value("ACTIVE"));

    mvc.perform(get("/v1/cards").with(user(USER_FIVE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(78)));
  }

  @Test
  void waitlistEndpointIsNotExposed() throws Exception {
    mvc.perform(post("/v1/waitlist").with(user(USER_ONE)))
        .andExpect(status().isNotFound());
  }

  @Test
  void signedWebhookIsRawBodyVerifiedProcessedAndDeduplicated() throws Exception {
    BillingAccount account = billingAccounts.findByOwnerId(USER_ONE).orElseThrow();
    UUID checkout = UUID.fromString("50000000-0000-0000-0000-000000000001");
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    billingCheckouts.save(BillingCheckout.builder()
        .id(checkout)
        .billingAccountId(account.getId())
        .planPriceId(UUID.fromString("30000000-0000-0000-0000-000000000001"))
        .paymentMethod(BillingPaymentMethod.PIX_AUTOMATIC)
        .status(BillingCheckoutStatus.PENDING)
        .idempotencyKey(UUID.randomUUID())
        .provider(BillingProvider.ABACATEPAY)
        .expiresAt(now.plusMinutes(30))
        .build());
    String payload = "{\"id\":\"evt-1\",\"event\":\"subscription.completed\",\"data\":{\"subscription\":{\"id\":\"sub-1\",\"currentPeriodStart\":\"" + now + "\",\"currentPeriodEnd\":\"" + now.plusMonths(
        1) + "\"},\"checkout\":{\"externalId\":\"" + checkout + "\"}}}";
    String signature = "valid-signature";
    when(abacatePayProvider.verifyWebhook(any(byte[].class), eq(signature)))
        .thenReturn(new PaymentWebhookEvent(
            "evt-1",
            BillingProviderEventType.COMPLETED,
            payload,
            "sub-1",
            null,
            checkout.toString(),
            now,
            now.plusMonths(1),
            null));
    for (int attempt = 0; attempt < 2; attempt++)
      mvc.perform(post("/v1/webhook/payment/abacatepay?webhookSecret=test-webhook-secret")
              .header("X-Webhook-Signature", signature).contentType(MediaType.APPLICATION_JSON).content(payload))
          .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
    mvc.perform(get("/v1/billing/subscription").with(user(USER_ONE)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    org.junit.jupiter.api.Assertions.assertEquals(1, billingProviderEvents.countByProviderEventId("evt-1"));
  }

  @Test
  void clientsSupportTheFullLifecycleAndAreIsolatedByOwner() throws Exception {
    MvcResult created = mvc.perform(post("/v1/clients").with(user(USER_ONE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"  Cliente Um  \",\"email\":\"client@example.com\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Cliente Um"))
        .andReturn();
    String clientId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

    mvc.perform(get("/v1/clients").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.total").value(1));

    mvc.perform(get("/v1/clients/{id}", clientId).with(user(USER_TWO)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Client not found."));

    mvc.perform(put("/v1/clients/{id}", clientId).with(user(USER_ONE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Cliente Atualizado\",\"notes\":\"observação\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Cliente Atualizado"));

    mvc.perform(post("/v1/clients/{id}/archive", clientId).with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    mvc.perform(get("/v1/clients?archived=true").with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(1)));

    mvc.perform(post("/v1/clients/{id}/restore", clientId).with(user(USER_ONE)))
        .andExpect(status().isOk());
    mvc.perform(delete("/v1/clients/{id}", clientId).with(user(USER_ONE)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    mvc.perform(get("/v1/clients/{id}", clientId).with(user(USER_ONE)))
        .andExpect(status().isNotFound());
  }

  @Test
  void readingCompletionAndCommentsEnforceTheLifecycle() throws Exception {
    MvcResult created = mvc.perform(post("/v1/readings").with(user(USER_ONE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"spreadId\":\"advice\",\"deckMode\":\"MAJOR\",\"title\":\"Conselho\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.positions", hasSize(1)))
        .andReturn();
    String body = created.getResponse().getContentAsString();
    String readingId = JsonPath.read(body, "$.id");
    String positionId = JsonPath.read(body, "$.positions[0].id");

    mvc.perform(post("/v1/readings/{id}/complete", readingId).with(user(USER_ONE)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Every position must have a card before completion."));

    mvc.perform(put("/v1/readings/{id}/positions/{position}", readingId, positionId).with(user(USER_ONE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"cardId\":\"the-fool\",\"orientation\":\"UPRIGHT\",\"interpretation\":\"Começar.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.card.id").value("the-fool"));
    mvc.perform(post("/v1/readings/{id}/complete", readingId).with(user(USER_ONE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
    mvc.perform(put("/v1/readings/{id}/positions/{position}", readingId, positionId).with(user(USER_ONE))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"cardId\":null,\"orientation\":null}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Card assignment is locked after completion."));

    MvcResult comment = mvc.perform(post("/v1/readings/{id}/comments", readingId).with(user(USER_ONE))
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Nota posterior\"}"))
        .andExpect(status().isCreated()).andReturn();
    String commentId = JsonPath.read(comment.getResponse().getContentAsString(), "$.id");
    mvc.perform(patch("/v1/readings/{id}/comments/{comment}", readingId, commentId).with(user(USER_ONE))
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Nota revisada\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("Nota revisada"));
    mvc.perform(delete("/v1/readings/{id}/comments/{comment}", readingId, commentId).with(user(USER_ONE)))
        .andExpect(status().isNoContent());
  }

  private void configureFounderCampaign() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID campaign = UUID.fromString("40000000-0000-0000-0000-000000000001");
    UUID monthly = UUID.fromString("40000000-0000-0000-0000-000000000002");
    UUID yearly = UUID.fromString("40000000-0000-0000-0000-000000000003");
    billingPlanPrices.save(BillingPlanPrice.builder()
        .id(monthly)
        .code("FOUNDER_MONTH")
        .name("Founder mensal")
        .billingInterval("MONTH")
        .amount(2900)
        .compareAtAmount(4900)
        .currency("BRL")
        .trialDays(14)
        .availablePaymentMethods(List.of("PIX_AUTOMATIC", "CARD"))
        .active(true)
        .defaultPlan(false)
        .build());
    billingPlanPrices.save(BillingPlanPrice.builder()
        .id(yearly)
        .code("FOUNDER_YEAR")
        .name("Founder anual")
        .billingInterval("YEAR")
        .amount(29000)
        .compareAtAmount(49000)
        .currency("BRL")
        .trialDays(14)
        .availablePaymentMethods(List.of("PIX_AUTOMATIC", "CARD"))
        .active(true)
        .defaultPlan(false)
        .build());
    billingPromotionCampaigns.save(BillingPromotionCampaign.builder()
        .id(campaign)
        .code("FOUNDERS")
        .name("Fundadores")
        .status("ACTIVE")
        .startsAt(now.minusDays(1))
        .endsAt(now.plusDays(29))
        .retentionPolicy("WHILE_SUBSCRIPTION_ACTIVE")
        .build());
    billingPromotionCampaignPrices.save(new BillingPromotionCampaignPrice(
        campaign,
        "MONTH",
        monthly,
        UUID.fromString("30000000-0000-0000-0000-000000000001")));
    billingPromotionCampaignPrices.save(new BillingPromotionCampaignPrice(
        campaign,
        "YEAR",
        yearly,
        UUID.fromString("30000000-0000-0000-0000-000000000002")));
  }

  private Profile profile(UUID id, String email, String locale) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return Profile.builder()
        .id(id)
        .email(email)
        .locale(locale)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
