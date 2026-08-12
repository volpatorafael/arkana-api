package com.arkana.controller;

import com.arkana.domain.Client;
import com.arkana.domain.Profile;
import com.arkana.repository.ClientRepository;
import com.arkana.service.BillingService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientControllerIT extends BaseControllerIT {

    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private BillingService billingService;

    private Profile firstUser;
    private Profile secondUser;

    @BeforeEach
    void setUpUsers() {
        firstUser = entityGeneratorService.randomProfile();
        secondUser = entityGeneratorService.randomProfile();
        billingService.startTrial(firstUser.getId());
        billingService.startTrial(secondUser.getId());
    }

    @Test
    void shouldListOnlyClientsOwnedByAuthenticatedUser() throws Exception {
        Client firstUserClient = entityGeneratorService.randomClient(firstUser);
        entityGeneratorService.randomClient(secondUser);

        mockMvcPerform(get("/v1/clients").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(firstUserClient.getId().toString()))
            .andExpect(jsonPath("$.items[0].name").value(firstUserClient.getName()))
            .andExpect(jsonPath("$.items[0].email").isEmpty())
            .andExpect(jsonPath("$.items[0].phone").isEmpty())
            .andExpect(jsonPath("$.items[0].notes").isEmpty())
            .andExpect(jsonPath("$.items[0].archivedAt").isEmpty())
            .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(25))
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void shouldListOnlyArchivedClientsOwnedByAuthenticatedUser() throws Exception {
        Client firstUserClient = entityGeneratorService.randomClient(firstUser);
        Client secondUserClient = entityGeneratorService.randomClient(secondUser);
        firstUserClient.archive();
        secondUserClient.archive();
        clientRepository.flush();

        mockMvcPerform(get("/v1/clients?archived=true").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(firstUserClient.getId().toString()))
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void shouldCreateClientForAuthenticatedUser() throws Exception {
        entityGeneratorService.randomClient(secondUser);

        String response = mockMvcPerform(post("/v1/clients")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"First client\",\"birthDate\":\"1990-06-15\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("First client"))
            .andExpect(jsonPath("$.birthDate").value("1990-06-15"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String clientId = JsonPath.read(response, "$.id");
        Client persisted = clientRepository.findById(java.util.UUID.fromString(clientId)).orElseThrow();
        assertThat(persisted.getOwnerId()).isEqualTo(firstUser.getId());
        assertThat(persisted.getOwnerId()).isNotEqualTo(secondUser.getId());
        assertThat(persisted.getBirthDate()).isEqualTo(LocalDate.of(1990, 6, 15));
    }

    @Test
    void shouldRejectClientBirthDateInTheFuture() throws Exception {
        mockMvcPerform(post("/v1/clients")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Future client\",\"birthDate\":\"2999-01-01\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldNotReturnAnotherUsersClient() throws Exception {
        Client firstClient = entityGeneratorService.randomClient(firstUser);
        Client secondClient = entityGeneratorService.randomClient(secondUser);

        mockMvcPerform(get("/v1/clients/{id}", firstClient.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(firstClient.getId().toString()));

        expectNotFound(
            mockMvcPerform(get("/v1/clients/{id}", secondClient.getId()).with(authenticatedAs(firstUser))),
            "Client not found.");
    }

    @Test
    void shouldNotUpdateAnotherUsersClient() throws Exception {
        Client firstClient = entityGeneratorService.randomClient(firstUser);
        Client secondClient = entityGeneratorService.randomClient(secondUser);
        String secondName = secondClient.getName();

        mockMvcPerform(put("/v1/clients/{id}", firstClient.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated own client\",\"birthDate\":\"1985-12-03\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated own client"))
            .andExpect(jsonPath("$.birthDate").value("1985-12-03"));

        expectNotFound(
            mockMvcPerform(put("/v1/clients/{id}", secondClient.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Leaked update\"}")),
            "Client not found.");

        assertThat(clientRepository.findById(secondClient.getId()).orElseThrow().getName()).isEqualTo(secondName);
    }

    @Test
    void shouldNotDeleteAnotherUsersClient() throws Exception {
        Client firstClient = entityGeneratorService.randomClient(firstUser);
        Client secondClient = entityGeneratorService.randomClient(secondUser);

        mockMvcPerform(delete("/v1/clients/{id}", firstClient.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isNoContent());
        assertThat(clientRepository.findById(firstClient.getId())).isEmpty();

        expectNotFound(
            mockMvcPerform(delete("/v1/clients/{id}", secondClient.getId()).with(authenticatedAs(firstUser))),
            "Client not found.");
        assertThat(clientRepository.findById(secondClient.getId())).isPresent();
    }

    @Test
    void shouldNotArchiveAnotherUsersClient() throws Exception {
        Client firstClient = entityGeneratorService.randomClient(firstUser);
        Client secondClient = entityGeneratorService.randomClient(secondUser);

        mockMvcPerform(post("/v1/clients/{id}/archive", firstClient.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        expectNotFound(
            mockMvcPerform(post("/v1/clients/{id}/archive", secondClient.getId())
                .with(authenticatedAs(firstUser))),
            "Client not found.");
        assertThat(clientRepository.findById(secondClient.getId()).orElseThrow().getArchivedAt()).isNull();
    }

    @Test
    void shouldNotRestoreAnotherUsersClient() throws Exception {
        Client firstClient = entityGeneratorService.randomClient(firstUser);
        Client secondClient = entityGeneratorService.randomClient(secondUser);
        firstClient.archive();
        secondClient.archive();
        clientRepository.flush();

        mockMvcPerform(post("/v1/clients/{id}/restore", firstClient.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt").doesNotExist());

        expectNotFound(
            mockMvcPerform(post("/v1/clients/{id}/restore", secondClient.getId())
                .with(authenticatedAs(firstUser))),
            "Client not found.");
        assertThat(clientRepository.findById(secondClient.getId()).orElseThrow().getArchivedAt()).isNotNull();
    }
}
