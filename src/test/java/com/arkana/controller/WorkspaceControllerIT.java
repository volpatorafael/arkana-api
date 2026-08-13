package com.arkana.controller;

import com.arkana.domain.BillingAccount;
import com.arkana.domain.BillingAccountStatus;
import com.arkana.domain.Client;
import com.arkana.domain.Profile;
import com.arkana.domain.Reading;
import com.arkana.domain.ReadingStatus;
import com.arkana.repository.BillingAccountRepository;
import com.arkana.repository.ReadingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceControllerIT extends BaseControllerIT {

  @Autowired
  private BillingAccountRepository accountRepository;
  @Autowired
  private ReadingRepository readingRepository;

  @Test
  void shouldBootstrapOnlyTheAuthenticatedUsersWorkspace() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    Profile otherUser = entityGeneratorService.randomProfile();
    Client client = entityGeneratorService.randomClient(user);
    entityGeneratorService.randomClient(otherUser);
    Reading reading = entityGeneratorService.randomReading(user, client);
    reading.setStatus(ReadingStatus.COMPLETED);
    Reading inProgressReading = entityGeneratorService.randomReading(user, client);
    inProgressReading.setStatus(ReadingStatus.IN_PROGRESS);
    for (int index = 0; index < 4; index++) {
      Reading completedReading = entityGeneratorService.randomReading(user, client);
      completedReading.setStatus(ReadingStatus.COMPLETED);
    }
    Reading otherReading = entityGeneratorService.randomReading(otherUser);
    otherReading.setStatus(ReadingStatus.COMPLETED);
    readingRepository.flush();

    mockMvcPerform(post("/v1/workspace/bootstrap?locale=en")
            .with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.profile.id").value(user.getId().toString()))
        .andExpect(jsonPath("$.billing.status").value("TRIALING"))
        .andExpect(jsonPath("$.billing.accessStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.plans").isEmpty())
        .andExpect(jsonPath("$.dashboard.activeClientCount").value(1))
        .andExpect(jsonPath("$.dashboard.inProgressReadingCount").value(1))
        .andExpect(jsonPath("$.dashboard.completedReadingCount").value(5))
        .andExpect(jsonPath("$.dashboard.recentReadings", hasSize(5)))
        .andExpect(jsonPath("$.dashboard.recentReadings[0].id").isNotEmpty())
        .andExpect(jsonPath("$.dashboard.recentReadings[0].spreadName").isNotEmpty())
        .andExpect(jsonPath("$.dashboard.recentReadings[0].status").isNotEmpty())
        .andExpect(jsonPath("$.dashboard.recentReadings[0].startedAt").isNotEmpty())
        .andExpect(jsonPath("$.dashboard.recentReadings[0].clientId").doesNotExist())
        .andExpect(jsonPath("$.dashboard.recentReadings[0].context").doesNotExist())
        .andExpect(jsonPath("$.dashboard.recentReadings[0].readingShareId").doesNotExist())
        .andExpect(jsonPath("$.dashboard.recentReadings[0].consultationFeeAmount").doesNotExist())
        .andExpect(jsonPath("$.dashboard.recentReadings[0].analysisVideoUrl").doesNotExist());
  }

  @Test
  void shouldReturnNoPrivateDataWhenProductAccessIsBlocked() throws Exception {
    Profile user = entityGeneratorService.randomProfile();
    BillingAccount account = entityGeneratorService.randomBillingAccount(user);
    account.setStatus(BillingAccountStatus.EXPIRED);
    account.setTrialEndsAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    account.setCurrentPeriodStart(null);
    account.setCurrentPeriodEnd(null);
    account.setOverrideEndsAt(null);
    accountRepository.flush();

    mockMvcPerform(post("/v1/workspace/bootstrap").with(authenticatedAs(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billing.accessStatus").value("BLOCKED"))
        .andExpect(jsonPath("$.plans", hasSize(2)))
        .andExpect(jsonPath("$.dashboard").isEmpty());
  }

  @Test
  void shouldValidateBootstrapLocale() throws Exception {
    Profile user = entityGeneratorService.randomProfile();

    mockMvcPerform(post("/v1/workspace/bootstrap?locale=invalid")
            .with(authenticatedAs(user)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldRequireAuthentication() throws Exception {
    mockMvcPerform(post("/v1/workspace/bootstrap"))
        .andExpect(status().isUnauthorized());
  }
}
