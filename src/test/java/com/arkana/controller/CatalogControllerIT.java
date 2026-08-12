package com.arkana.controller;

import com.arkana.domain.Profile;
import com.arkana.service.BillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogControllerIT extends BaseControllerIT {

    @Autowired
    private BillingService billingService;

    private Profile firstUser;
    private Profile secondUser;
    private Profile blockedUser;

    @BeforeEach
    void setUpUsers() {
        firstUser = entityGeneratorService.randomProfile();
        secondUser = entityGeneratorService.randomProfile();
        blockedUser = entityGeneratorService.randomProfile();
        billingService.startTrial(firstUser.getId());
        billingService.startTrial(secondUser.getId());
    }

    @Test
    void shouldReturnSharedCardCatalogOnlyToUsersWithProductAccess() throws Exception {
        String firstResponse = mockMvcPerform(get("/v1/cards?deckMode=MAJOR&locale=en")
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(22)))
            .andExpect(jsonPath("$[0].id").isNotEmpty())
            .andExpect(jsonPath("$[0].number").isNumber())
            .andExpect(jsonPath("$[0].suit").value("major"))
            .andExpect(jsonPath("$[0].name").isNotEmpty())
            .andExpect(jsonPath("$[0].description").isNotEmpty())
            .andExpect(jsonPath("$[0].lightMeaning").isNotEmpty())
            .andExpect(jsonPath("$[0].shadowMeaning").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String secondResponse = mockMvcPerform(get("/v1/cards?deckMode=MAJOR&locale=en")
                .with(authenticatedAs(secondUser)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(secondResponse).isEqualTo(firstResponse);

        mockMvcPerform(get("/v1/cards").with(authenticatedAs(blockedUser)))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnSharedSpreadCatalogOnlyToUsersWithProductAccess() throws Exception {
        String firstResponse = mockMvcPerform(get("/v1/spreads?locale=en").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(12)))
            .andReturn().getResponse().getContentAsString();
        String secondResponse = mockMvcPerform(get("/v1/spreads?locale=en").with(authenticatedAs(secondUser)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(secondResponse).isEqualTo(firstResponse);

        mockMvcPerform(get("/v1/spreads").with(authenticatedAs(blockedUser)))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnSharedSpreadOnlyToUsersWithProductAccess() throws Exception {
        String firstResponse = mockMvcPerform(get("/v1/spreads/advice?locale=en")
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("advice"))
            .andExpect(jsonPath("$.name").isNotEmpty())
            .andExpect(jsonPath("$.shortDescription").isNotEmpty())
            .andExpect(jsonPath("$.description").isNotEmpty())
            .andExpect(jsonPath("$.useCase").isNotEmpty())
            .andExpect(jsonPath("$.positionCount").value(1))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.positions[0].id").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].key").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].order").value(1))
            .andExpect(jsonPath("$.positions[0].name").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].meaning").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].x").isNumber())
            .andExpect(jsonPath("$.positions[0].y").isNumber())
            .andExpect(jsonPath("$.positions[0].rotation").isNumber())
            .andReturn().getResponse().getContentAsString();
        String secondResponse = mockMvcPerform(get("/v1/spreads/advice?locale=en")
                .with(authenticatedAs(secondUser)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(secondResponse).isEqualTo(firstResponse);

        mockMvcPerform(get("/v1/spreads/advice").with(authenticatedAs(blockedUser)))
            .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnLocalizedDiamondSpreadWithItsLayout() throws Exception {
        mockMvcPerform(get("/v1/spreads/diamond?locale=pt-BR")
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("diamond"))
            .andExpect(jsonPath("$.name").value("Diamante"))
            .andExpect(jsonPath("$.positionCount").value(5))
            .andExpect(jsonPath("$.positions", hasSize(5)))
            .andExpect(jsonPath("$.positions[0].key").value("1"))
            .andExpect(jsonPath("$.positions[0].meaning").value("A questão ou assunto que precisa de mais clareza."))
            .andExpect(jsonPath("$.positions[0].x").value(50.0))
            .andExpect(jsonPath("$.positions[0].y").value(50.0))
            .andExpect(jsonPath("$.positions[1].x").value(25.0))
            .andExpect(jsonPath("$.positions[2].x").value(75.0))
            .andExpect(jsonPath("$.positions[3].y").value(82.0))
            .andExpect(jsonPath("$.positions[4].y").value(18.0));
    }

    @Test
    void shouldReturnLocalizedDecisionSpreadWithItsLayout() throws Exception {
        mockMvcPerform(get("/v1/spreads/decision?locale=pt-BR")
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("decision"))
            .andExpect(jsonPath("$.name").value("Decisão"))
            .andExpect(jsonPath("$.positionCount").value(6))
            .andExpect(jsonPath("$.positions", hasSize(6)))
            .andExpect(jsonPath("$.positions[0].meaning").value("Energia geral que envolve a questão."))
            .andExpect(jsonPath("$.positions[0].x").value(15.0))
            .andExpect(jsonPath("$.positions[1].x").value(40.0))
            .andExpect(jsonPath("$.positions[1].y").value(25.0))
            .andExpect(jsonPath("$.positions[2].x").value(60.0))
            .andExpect(jsonPath("$.positions[3].y").value(75.0))
            .andExpect(jsonPath("$.positions[4].y").value(75.0))
            .andExpect(jsonPath("$.positions[5].x").value(85.0));
    }

    @Test
    void shouldReturnLocalizedBlindSpotSpreadWithItsLayout() throws Exception {
        mockMvcPerform(get("/v1/spreads/blind-spot?locale=pt-BR")
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("blind-spot"))
            .andExpect(jsonPath("$.name").value("Ponto Cego"))
            .andExpect(jsonPath("$.positionCount").value(4))
            .andExpect(jsonPath("$.positions", hasSize(4)))
            .andExpect(jsonPath("$.positions[0].name").value("Identidade pessoal"))
            .andExpect(jsonPath("$.positions[0].x").value(35.0))
            .andExpect(jsonPath("$.positions[0].y").value(30.0))
            .andExpect(jsonPath("$.positions[1].name").value("O grande desconhecido"))
            .andExpect(jsonPath("$.positions[1].x").value(65.0))
            .andExpect(jsonPath("$.positions[1].y").value(70.0))
            .andExpect(jsonPath("$.positions[2].x").value(35.0))
            .andExpect(jsonPath("$.positions[2].y").value(70.0))
            .andExpect(jsonPath("$.positions[3].x").value(65.0))
            .andExpect(jsonPath("$.positions[3].y").value(30.0));
    }

    @Test
    void shouldReturnLocalizedTempleOfZeusSpreadWithItsLayout() throws Exception {
        mockMvcPerform(get("/v1/spreads/temple-of-zeus?locale=pt-BR")
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("temple-of-zeus"))
            .andExpect(jsonPath("$.name").value("Templo de Zeus"))
            .andExpect(jsonPath("$.positionCount").value(4))
            .andExpect(jsonPath("$.positions", hasSize(4)))
            .andExpect(jsonPath("$.positions[0].name").value("Passado recente"))
            .andExpect(jsonPath("$.positions[0].x").value(22.0))
            .andExpect(jsonPath("$.positions[0].y").value(50.0))
            .andExpect(jsonPath("$.positions[1].name").value("Situação atual"))
            .andExpect(jsonPath("$.positions[1].x").value(50.0))
            .andExpect(jsonPath("$.positions[1].y").value(78.0))
            .andExpect(jsonPath("$.positions[2].x").value(78.0))
            .andExpect(jsonPath("$.positions[2].y").value(50.0))
            .andExpect(jsonPath("$.positions[3].name").value("Conselho financeiro"))
            .andExpect(jsonPath("$.positions[3].x").value(50.0))
            .andExpect(jsonPath("$.positions[3].y").value(22.0));
    }
}
