package com.arkana.controller;

import com.arkana.domain.Profile;
import com.arkana.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerIT extends BaseControllerIT {

    @Autowired
    private ProfileRepository profileRepository;

    @Test
    void shouldReturnOnlyTheAuthenticatedUsersProfile() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();

        mockMvcPerform(get("/v1/profile").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(firstUser.getId().toString()))
            .andExpect(jsonPath("$.email").value(firstUser.getEmail()))
            .andExpect(jsonPath("$.displayName").isEmpty())
            .andExpect(jsonPath("$.locale").value(firstUser.getLocale()))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(secondUser.getId().toString())));
    }

    @Test
    void shouldUpdateOnlyTheAuthenticatedUsersProfile() throws Exception {
        Profile firstUser = entityGeneratorService.randomProfile();
        Profile secondUser = entityGeneratorService.randomProfile();
        String secondUserLocale = secondUser.getLocale();

        mockMvcPerform(patch("/v1/profile")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"First reader\",\"locale\":\"en\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(firstUser.getId().toString()))
            .andExpect(jsonPath("$.displayName").value("First reader"))
            .andExpect(jsonPath("$.locale").value("en"));

        Profile persistedFirst = profileRepository.findById(firstUser.getId()).orElseThrow();
        Profile persistedSecond = profileRepository.findById(secondUser.getId()).orElseThrow();
        assertThat(persistedFirst.getDisplayName()).isEqualTo("First reader");
        assertThat(persistedFirst.getLocale()).isEqualTo("en");
        assertThat(persistedSecond.getDisplayName()).isNull();
        assertThat(persistedSecond.getLocale()).isEqualTo(secondUserLocale);
    }
}
