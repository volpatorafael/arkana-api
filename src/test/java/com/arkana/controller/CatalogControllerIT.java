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
            .andExpect(jsonPath("$", hasSize(8)))
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
            .andReturn().getResponse().getContentAsString();
        String secondResponse = mockMvcPerform(get("/v1/spreads/advice?locale=en")
                .with(authenticatedAs(secondUser)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(secondResponse).isEqualTo(firstResponse);

        mockMvcPerform(get("/v1/spreads/advice").with(authenticatedAs(blockedUser)))
            .andExpect(status().isForbidden());
    }
}
