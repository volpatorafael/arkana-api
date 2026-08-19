package com.arkana.controller;

import com.arkana.TestDataGenerator;
import com.arkana.domain.AdminRole;
import com.arkana.domain.AdminUser;
import com.arkana.domain.Deck;
import com.arkana.domain.Profile;
import com.arkana.domain.Reading;
import com.arkana.domain.ReadingStatus;
import com.arkana.integration.IdentityAnalyticsProvider;
import com.arkana.integration.IdentityAnalyticsProvider.IdentityUser;
import com.arkana.repository.AdminUserRepository;
import com.arkana.repository.ProfileRepository;
import com.arkana.repository.ReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAnalyticsControllerIT extends BaseControllerIT {
  @Autowired
  private AdminUserRepository adminUsers;

  @Autowired
  private ProfileRepository profiles;

  @Autowired
  private ReadingRepository readings;

  @MockitoBean
  private IdentityAnalyticsProvider identityProvider;

  private Profile adminIdentity;

  @BeforeEach
  void createAdmin() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID adminId = UUID.randomUUID();
    adminIdentity = Profile.builder()
        .id(adminId)
        .email("admin@arkana.test")
        .locale("pt-BR")
        .createdAt(now)
        .updatedAt(now)
        .build();
    adminUsers.saveAndFlush(AdminUser.builder()
        .userId(adminId)
        .role(AdminRole.ADMIN)
        .active(true)
        .createdAt(now)
        .build());
  }

  @Test
  void verifiesActiveAdministrativeAccess() throws Exception {
    mockMvcPerform(get("/v1/admin/session").with(authenticatedAs(adminIdentity)))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  @Test
  void requiresAuthenticationForAdministrativeAccess() throws Exception {
    mockMvcPerform(get("/v1/admin/session"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @ParameterizedTest(name = "rejects regular Arkana user on {0}")
  @CsvSource({
      "/v1/admin/session",
      "/v1/admin/analytics/overview",
      "/v1/admin/analytics/identity",
      "/v1/admin/decks",
      "/v1/admin/decks/rider-waite",
      "/v1/admin/decks/rider-waite/cards"
  })
  void rejectsAuthenticatedNonAdministratorFromEveryEndpoint(String endpoint) throws Exception {
    Profile regularUser = profiles.saveAndFlush(TestDataGenerator.randomProfile().build());
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    mockMvcPerform(get(endpoint)
            .param("from", today.toString())
            .param("to", today.toString())
            .param("timeZone", "UTC")
            .with(authenticatedAs(regularUser)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void rejectsAuthenticatedNonAdministratorFromDeckCreateAndCardMutations() throws Exception {
    Profile regularUser = profiles.saveAndFlush(TestDataGenerator.randomProfile().build());
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    // POST /decks
    mockMvcPerform(post("/v1/admin/decks")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"test-deck\",\"namePtBr\":\"Test\",\"nameEn\":\"Test\",\"displayOrder\":99}")
            .with(authenticatedAs(regularUser)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    // PUT /decks/{id}
    mockMvcPerform(put("/v1/admin/decks/rider-waite")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"namePtBr\":\"Hacked\"}")
            .with(authenticatedAs(regularUser)))
        .andExpect(status().isForbidden());

    // POST /decks/{id}/cards
    mockMvcPerform(post("/v1/admin/decks/rider-waite/cards")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"hack-card\",\"number\":99,\"suit\":\"major\",\"imagePath\":\"x.png\",\"namePtBr\":\"x\",\"nameEn\":\"x\",\"descriptionPtBr\":\"x\",\"descriptionEn\":\"x\",\"lightPtBr\":\"x\",\"lightEn\":\"x\",\"shadowPtBr\":\"x\",\"shadowEn\":\"x\"}")
            .with(authenticatedAs(regularUser)))
        .andExpect(status().isForbidden());

    // PUT card
    mockMvcPerform(put("/v1/admin/decks/rider-waite/cards/the-fool")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"namePtBr\":\"Hacked\",\"nameEn\":\"Hacked\",\"descriptionPtBr\":\"x\",\"descriptionEn\":\"x\",\"lightPtBr\":\"x\",\"lightEn\":\"x\",\"shadowPtBr\":\"x\",\"shadowEn\":\"x\"}")
            .with(authenticatedAs(regularUser)))
        .andExpect(status().isForbidden());
  }

  @Test
  void deactivationBlocksTheNextRequest() throws Exception {
    mockMvcPerform(get("/v1/admin/session").with(authenticatedAs(adminIdentity)))
        .andExpect(status().isNoContent());

    AdminUser admin = adminUsers.findById(adminIdentity.getId()).orElseThrow();
    admin.setActive(false);
    adminUsers.saveAndFlush(admin);

    mockMvcPerform(get("/v1/admin/session").with(authenticatedAs(adminIdentity)))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsOnlyAggregatedProductAnalytics() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    LocalDate today = now.toLocalDate();
    adminUsers.saveAndFlush(AdminUser.builder()
        .userId(UUID.randomUUID())
        .role(AdminRole.MARKETING)
        .active(false)
        .createdAt(now)
        .build());
    Profile reader = profiles.saveAndFlush(Profile.builder()
        .id(UUID.randomUUID())
        .email("reader@arkana.test")
        .locale("pt-BR")
        .createdAt(now)
        .updatedAt(now)
        .build());
    entityGeneratorService.randomClient(reader);
    Reading reading = TestDataGenerator.randomReading(reader, null)
        .deckId(Deck.DEFAULT_DECK_ID)
        .status(ReadingStatus.COMPLETED)
        .startedAt(now)
        .completedAt(now)
        .createdAt(now)
        .updatedAt(now)
        .build();
    readings.saveAndFlush(reading);

    mockMvcPerform(get("/v1/admin/analytics/overview")
            .param("from", today.toString())
            .param("to", today.toString())
            .param("timeZone", "UTC")
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.kpis.adminAccesses.value").value(2))
        .andExpect(jsonPath("$.kpis.activatedAccounts.value").value(1))
        .andExpect(jsonPath("$.kpis.dailyActiveUsers.value").value(1))
        .andExpect(jsonPath("$.kpis.weeklyActiveUsers.value").value(1))
        .andExpect(jsonPath("$.timeline", hasSize(1)))
        .andExpect(jsonPath("$.timeline[0].registrations").value(1))
        .andExpect(jsonPath("$.timeline[0].completedReadings").value(1))
        .andExpect(jsonPath("$", not(hasKey("users"))))
        .andExpect(jsonPath("$", not(hasKey("emails"))));
  }

  @Test
  void returnsIdentityAggregatesAndExcludesAdminsAndAnonymousUsers() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Profile activated = profiles.saveAndFlush(Profile.builder()
        .id(UUID.randomUUID())
        .email("activated@arkana.test")
        .locale("pt-BR")
        .createdAt(now)
        .updatedAt(now)
        .build());
    when(identityProvider.users()).thenReturn(List.of(
        new IdentityUser(activated.getId(), now, now, now, false),
        new IdentityUser(UUID.randomUUID(), now, null, null, false),
        new IdentityUser(UUID.randomUUID(), now, now, now, false),
        new IdentityUser(adminIdentity.getId(), now, now, now, false),
        new IdentityUser(UUID.randomUUID(), now, now, now, true)));

    mockMvcPerform(get("/v1/admin/analytics/identity")
            .param("from", now.toLocalDate().toString())
            .param("to", now.toLocalDate().toString())
            .param("timeZone", "UTC")
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metrics.createdAccounts.value").value(3))
        .andExpect(jsonPath("$.metrics.awaitingConfirmation.value").value(1))
        .andExpect(jsonPath("$.metrics.confirmedAccounts.value").value(2))
        .andExpect(jsonPath("$.metrics.signedInAccounts.value").value(2))
        .andExpect(jsonPath("$.metrics.signedInNotActivated.value").value(1))
        .andExpect(jsonPath("$.metrics.confirmedNotActivated.value").value(1))
        .andExpect(jsonPath("$.metrics.activatedWorkspaces.value").value(1))
        .andExpect(jsonPath("$.registrationFunnel[0].key").value("ACCOUNT_CREATED"))
        .andExpect(jsonPath("$.registrationFunnel[0].count").value(3))
        .andExpect(jsonPath("$.registrationFunnel[1].key").value("EMAIL_CONFIRMED"))
        .andExpect(jsonPath("$.registrationFunnel[1].count").value(2))
        .andExpect(jsonPath("$.registrationFunnel[2].key").value("SESSION_STARTED"))
        .andExpect(jsonPath("$.registrationFunnel[2].count").value(2))
        .andExpect(jsonPath("$.registrationFunnel[3].key").value("WORKSPACE_ACTIVATED"))
        .andExpect(jsonPath("$.registrationFunnel[3].count").value(1))
        .andExpect(jsonPath("$.productFunnel[0].key").value("WORKSPACE_ACTIVATED"))
        .andExpect(jsonPath("$.productFunnel[0].count").value(1))
        .andExpect(jsonPath("$.productFunnel[1].key").value("FIRST_CLIENT_CREATED"))
        .andExpect(jsonPath("$.productFunnel[1].count").value(0))
        .andExpect(jsonPath("$", not(hasKey("users"))))
        .andExpect(jsonPath("$", not(hasKey("email"))));
  }

  @Test
  void rejectsInvalidReportingPeriod() throws Exception {
    mockMvcPerform(get("/v1/admin/analytics/overview")
            .param("from", "2026-08-19")
            .param("to", "2026-08-18")
            .param("timeZone", "UTC")
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
