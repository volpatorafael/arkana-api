package com.arkana.controller;

import com.arkana.TestDataGenerator;
import com.arkana.domain.AdminRole;
import com.arkana.domain.AdminUser;
import com.arkana.domain.Profile;
import com.arkana.service.BillingService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCatalogControllerIT extends BaseControllerIT {
  @Autowired
  private com.arkana.repository.AdminUserRepository adminUsers;

  @Autowired
  private BillingService billingService;

  @Autowired
  private EntityManager entityManager;

  private Profile adminIdentity;

  @BeforeEach
  void createAdmin() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID adminId = UUID.randomUUID();
    adminIdentity = Profile.builder()
        .id(adminId)
        .email("admin-catalog@arkana.test")
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
  void adminCanListAndGetDecks() throws Exception {
    mockMvcPerform(get("/v1/admin/decks").with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
        .andExpect(jsonPath("$[?(@.id == 'rider-waite')].namePtBr").value("Rider-Waite"))
        .andExpect(jsonPath("$[?(@.id == 'rider-waite')].active").value(true));

    mockMvcPerform(get("/v1/admin/decks/rider-waite").with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("rider-waite"))
        .andExpect(jsonPath("$.cardCount").value(78));
  }

  @Test
  void adminCanCreateUpdateDeck() throws Exception {
    String newId = "test-deck-it-" + UUID.randomUUID().toString().substring(0, 8);

    mockMvcPerform(post("/v1/admin/decks")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"" + newId + "\",\"namePtBr\":\"Test Deck\",\"nameEn\":\"Test Deck\",\"displayOrder\":99,\"active\":false}")
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(newId))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.cardCount").value(0));

    mockMvcPerform(put("/v1/admin/decks/" + newId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"namePtBr\":\"Test Deck Atualizado\",\"active\":true,\"displayOrder\":5}")
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.namePtBr").value("Test Deck Atualizado"))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.displayOrder").value(5));
  }

  @Test
  void adminCanListAndUpdateCardContent() throws Exception {
    // original values from seed
    mockMvcPerform(get("/v1/admin/decks/rider-waite/cards").with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(78)))
        .andExpect(jsonPath("$[?(@.id == 'the-fool')].namePtBr").value("O Louco"));

    String updateJson = "{\"namePtBr\":\"O Louco Editado\",\"nameEn\":\"The Fool Edited\"," +
        "\"descriptionPtBr\":\"desc pt\",\"descriptionEn\":\"desc en\"," +
        "\"lightPtBr\":\"luz pt\",\"lightEn\":\"light en\"," +
        "\"shadowPtBr\":\"sombra pt\",\"shadowEn\":\"shadow en\"}";

    mockMvcPerform(put("/v1/admin/decks/rider-waite/cards/the-fool")
            .contentType(MediaType.APPLICATION_JSON)
            .content(updateJson)
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.namePtBr").value("O Louco Editado"))
        .andExpect(jsonPath("$.nameEn").value("The Fool Edited"))
        .andExpect(jsonPath("$.descriptionPtBr").value("desc pt"))
        .andExpect(jsonPath("$.lightPtBr").value("luz pt"));

    // verify it sticks via list
    mockMvcPerform(get("/v1/admin/decks/rider-waite/cards").with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'the-fool')].namePtBr").value("O Louco Editado"));
  }

  @Test
  void adminCanCreateCard() throws Exception {
    String cardId = "test-card-it-" + UUID.randomUUID().toString().substring(0, 8);

    String createJson = "{\"id\":\"" + cardId + "\",\"number\":99,\"suit\":\"major\",\"imagePath\":\"99-Test.png\"," +
        "\"namePtBr\":\"Carta Teste\",\"nameEn\":\"Test Card\"," +
        "\"descriptionPtBr\":\"dpt\",\"descriptionEn\":\"den\"," +
        "\"lightPtBr\":\"lpt\",\"lightEn\":\"len\"," +
        "\"shadowPtBr\":\"spt\",\"shadowEn\":\"sen\"}";

    mockMvcPerform(post("/v1/admin/decks/rider-waite/cards")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createJson)
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(cardId))
        .andExpect(jsonPath("$.deckId").value("rider-waite"))
        .andExpect(jsonPath("$.number").value(99))
        .andExpect(jsonPath("$.namePtBr").value("Carta Teste"));

    // deck cardCount should have increased (at least checked via list size or separate get)
    mockMvcPerform(get("/v1/admin/decks/rider-waite").with(authenticatedAs(adminIdentity)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardCount", greaterThanOrEqualTo(79)));
  }

  @Test
  void adminDeckOperationsReturn404ForMissing() throws Exception {
    expectNotFound(
        mockMvcPerform(get("/v1/admin/decks/does-not-exist").with(authenticatedAs(adminIdentity))),
        "Deck not found");

    expectNotFound(
        mockMvcPerform(put("/v1/admin/decks/does-not-exist")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"namePtBr\":\"x\"}")
            .with(authenticatedAs(adminIdentity))),
        "Deck not found");

    expectNotFound(
        mockMvcPerform(get("/v1/admin/decks/does-not-exist/cards").with(authenticatedAs(adminIdentity))),
        "Deck not found");
  }

  @Test
  void adminRejectsDuplicateDeckAndCard() throws Exception {
    // duplicate deck
    mockMvcPerform(post("/v1/admin/decks")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"rider-waite\",\"namePtBr\":\"dup\",\"nameEn\":\"dup\",\"displayOrder\":1}")
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    // duplicate card
    mockMvcPerform(post("/v1/admin/decks/rider-waite/cards")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"the-fool\",\"number\":0,\"suit\":\"major\",\"imagePath\":\"00.png\"," +
                "\"namePtBr\":\"dup\",\"nameEn\":\"dup\",\"descriptionPtBr\":\"d\",\"descriptionEn\":\"d\"," +
                "\"lightPtBr\":\"l\",\"lightEn\":\"l\",\"shadowPtBr\":\"s\",\"shadowEn\":\"s\"}")
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isConflict());
  }

  @Test
  void adminRejectsInvalidUpdateCardPayload() throws Exception {
    mockMvcPerform(put("/v1/admin/decks/rider-waite/cards/the-fool")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"namePtBr\":\"\"}")  // missing required fields + empty
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deletingCardReferencedByReadingFailsWithConstraintViolation() throws Exception {
    // create a regular user with access
    Profile owner = entityGeneratorService.randomProfile();
    billingService.startTrial(owner.getId());

    // create a freeform reading
    String createReading = mockMvcPerform(post("/v1/readings")
            .with(authenticatedAs(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"spreadId\":\"free\",\"deckMode\":\"FULL\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String readingId = JsonPath.read(createReading, "$.id");

    // add a position using "the-fool" (this creates the FK reference)
    mockMvcPerform(post("/v1/readings/{readingId}/positions", readingId)
            .with(authenticatedAs(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"cardId\":\"the-fool\",\"orientation\":\"UPRIGHT\"}"))
        .andExpect(status().isCreated());

    entityManager.flush();
    entityManager.clear();

    // now try to delete the card via admin — must hit DB FK constraint (DataIntegrityViolation)
    mockMvcPerform(delete("/v1/admin/decks/rider-waite/cards/the-fool")
            .with(authenticatedAs(adminIdentity)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(409));
  }
}
