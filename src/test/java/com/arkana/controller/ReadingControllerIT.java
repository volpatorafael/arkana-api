package com.arkana.controller;

import com.arkana.domain.CardOrientation;
import com.arkana.domain.Client;
import com.arkana.domain.CurrencyCode;
import com.arkana.domain.Profile;
import com.arkana.domain.Reading;
import com.arkana.domain.ReadingComment;
import com.arkana.domain.ReadingDeckMode;
import com.arkana.domain.ReadingPosition;
import com.arkana.domain.ReadingStatus;
import com.arkana.domain.SpreadPosition;
import com.arkana.repository.ReadingCommentRepository;
import com.arkana.repository.ReadingPositionRepository;
import com.arkana.repository.ReadingRepository;
import com.arkana.repository.SpreadPositionRepository;
import com.arkana.service.BillingService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadingControllerIT extends BaseControllerIT {

    @Autowired
    private ReadingRepository readingRepository;
    @Autowired
    private ReadingPositionRepository positionRepository;
    @Autowired
    private ReadingCommentRepository commentRepository;
    @Autowired
    private SpreadPositionRepository spreadPositionRepository;
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
    void shouldListOnlyReadingsOwnedByAuthenticatedUser() throws Exception {
        Client firstClient = entityGeneratorService.randomClient(firstUser);
        Client secondClient = entityGeneratorService.randomClient(secondUser);
        Reading firstReading = reading(firstUser, firstClient);
        Reading secondReading = reading(secondUser, secondClient);

        mockMvcPerform(get("/v1/readings").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(firstReading.getId().toString()))
            .andExpect(jsonPath("$.items[0].clientId").value(firstClient.getId().toString()))
            .andExpect(jsonPath("$.items[0].readingShareId").isEmpty())
            .andExpect(jsonPath("$.items[0].spreadId").value("advice"))
            .andExpect(jsonPath("$.items[0].spreadName").value("Carta Conselho"))
            .andExpect(jsonPath("$.items[0].deckMode").value("MAJOR"))
            .andExpect(jsonPath("$.items[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.items[0].title").value(firstReading.getTitle()))
            .andExpect(jsonPath("$.items[0].question").isEmpty())
            .andExpect(jsonPath("$.items[0].context").isEmpty())
            .andExpect(jsonPath("$.items[0].consultationFeeAmount").isEmpty())
            .andExpect(jsonPath("$.items[0].consultationFeeCurrency").value("BRL"))
            .andExpect(jsonPath("$.items[0].consultationDurationMinutes").isEmpty())
            .andExpect(jsonPath("$.items[0].startedAt").isNotEmpty())
            .andExpect(jsonPath("$.items[0].completedAt").isEmpty())
            .andExpect(jsonPath("$.items[0].archivedAt").isEmpty())
            .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(25))
            .andExpect(jsonPath("$.total").value(1));

        mockMvcPerform(get("/v1/readings?clientId={clientId}", secondClient.getId())
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)))
            .andExpect(jsonPath("$.total").value(0));

        secondReading.archive(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        readingRepository.flush();
        mockMvcPerform(get("/v1/readings?archived=true").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void shouldCreateReadingForAuthenticatedUserAndRejectAnotherUsersClient() throws Exception {
        Client firstClient = entityGeneratorService.randomClient(firstUser);
        Client secondClient = entityGeneratorService.randomClient(secondUser);

        String response = mockMvcPerform(post("/v1/readings")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + firstClient.getId()
                    + "\",\"spreadId\":\"advice\",\"deckMode\":\"MAJOR\","
                    + "\"consultationFeeAmount\":15000,\"consultationDurationMinutes\":90,"
                    + "\"analysisVideoUrl\":\"https://www.youtube.com/watch?v=abc123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.clientId").value(firstClient.getId().toString()))
            .andExpect(jsonPath("$.readingShareId").isEmpty())
            .andExpect(jsonPath("$.spreadId").value("advice"))
            .andExpect(jsonPath("$.spreadName").value("Carta Conselho"))
            .andExpect(jsonPath("$.deckMode").value("MAJOR"))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.title").isEmpty())
            .andExpect(jsonPath("$.question").isEmpty())
            .andExpect(jsonPath("$.context").isEmpty())
            .andExpect(jsonPath("$.consultationFeeAmount").value(15000))
            .andExpect(jsonPath("$.consultationFeeCurrency").value("BRL"))
            .andExpect(jsonPath("$.consultationDurationMinutes").value(90))
            .andExpect(jsonPath("$.analysisVideoUrl")
                .value("https://www.youtube.com/watch?v=abc123"))
            .andExpect(jsonPath("$.startedAt").isNotEmpty())
            .andExpect(jsonPath("$.completedAt").isEmpty())
            .andExpect(jsonPath("$.archivedAt").isEmpty())
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andExpect(jsonPath("$.spread.id").value("advice"))
            .andExpect(jsonPath("$.spread.name").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].id").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].key").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].order").value(1))
            .andExpect(jsonPath("$.positions[0].name").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].meaning").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].x").isNumber())
            .andExpect(jsonPath("$.positions[0].y").isNumber())
            .andExpect(jsonPath("$.positions[0].rotation").isNumber())
            .andExpect(jsonPath("$.positions[0].card").isEmpty())
            .andExpect(jsonPath("$.positions[0].orientation").isEmpty())
            .andExpect(jsonPath("$.positions[0].interpretation").isEmpty())
            .andExpect(jsonPath("$.positions[0].createdAt").isNotEmpty())
            .andExpect(jsonPath("$.positions[0].updatedAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID createdId = UUID.fromString(JsonPath.read(response, "$.id"));
        Reading created = readingRepository.findById(createdId).orElseThrow();
        assertThat(created.getOwnerId()).isEqualTo(firstUser.getId());
        assertThat(created.getConsultationFeeAmount()).isEqualTo(15000);
        assertThat(created.getConsultationFeeCurrency()).isEqualTo(CurrencyCode.BRL);
        assertThat(created.getConsultationDurationMinutes()).isEqualTo(90);
        assertThat(created.getAnalysisVideoUrl())
            .isEqualTo("https://www.youtube.com/watch?v=abc123");

        long countBeforeForeignAttempt = readingRepository.count();
        expectNotFound(
            mockMvcPerform(post("/v1/readings")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + secondClient.getId()
                    + "\",\"spreadId\":\"advice\",\"deckMode\":\"MAJOR\"}")),
            "Client not found.");
        assertThat(readingRepository.count()).isEqualTo(countBeforeForeignAttempt);
    }

    @Test
    void shouldRejectInvalidConsultationDetails() throws Exception {
        mockMvcPerform(post("/v1/readings")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spreadId\":\"advice\",\"deckMode\":\"MAJOR\","
                    + "\"consultationFeeAmount\":-1}"))
            .andExpect(status().isBadRequest());

        mockMvcPerform(post("/v1/readings")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spreadId\":\"advice\",\"deckMode\":\"MAJOR\","
                    + "\"consultationDurationMinutes\":0}"))
            .andExpect(status().isBadRequest());

        mockMvcPerform(post("/v1/readings")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spreadId\":\"advice\",\"deckMode\":\"MAJOR\","
                    + "\"analysisVideoUrl\":\"http://video.example/analysis\"}"))
            .andExpect(status().isBadRequest());

        mockMvcPerform(post("/v1/readings")
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spreadId\":\"advice\",\"deckMode\":\"MAJOR\","
                    + "\"analysisVideoUrl\":\"https://video.example/analysis\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldLocalizeSpreadNameInReadingListAndDetail() throws Exception {
        Reading reading = reading(firstUser);

        mockMvcPerform(get("/v1/readings?locale=en").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].spreadId").value("advice"))
            .andExpect(jsonPath("$.items[0].spreadName").value("Advice Card"));

        mockMvcPerform(get("/v1/readings/{id}?locale=en", reading.getId())
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spreadId").value("advice"))
            .andExpect(jsonPath("$.spreadName").value("Advice Card"));
    }

    @Test
    void shouldNotReturnAnotherUsersReading() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);

        mockMvcPerform(get("/v1/readings/{id}", firstReading.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(firstReading.getId().toString()));

        expectNotFound(
            mockMvcPerform(get("/v1/readings/{id}", secondReading.getId()).with(authenticatedAs(firstUser))),
            "Reading not found.");
    }

    @Test
    void shouldNotUpdateAnotherUsersReadingOrAttachAnotherUsersClient() throws Exception {
        Client firstClient = entityGeneratorService.randomClient(firstUser);
        Client secondClient = entityGeneratorService.randomClient(secondUser);
        Reading firstReading = reading(firstUser, firstClient);
        Reading secondReading = reading(secondUser, secondClient);
        String secondTitle = secondReading.getTitle();

        mockMvcPerform(patch("/v1/readings/{id}", firstReading.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated own reading\","
                    + "\"consultationFeeAmount\":22550,\"consultationDurationMinutes\":75}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated own reading"))
            .andExpect(jsonPath("$.consultationFeeAmount").value(22550))
            .andExpect(jsonPath("$.consultationFeeCurrency").value("BRL"))
            .andExpect(jsonPath("$.consultationDurationMinutes").value(75));

        expectNotFound(
            mockMvcPerform(patch("/v1/readings/{id}", secondReading.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Leaked update\"}")),
            "Reading not found.");
        assertThat(readingRepository.findById(secondReading.getId()).orElseThrow().getTitle())
            .isEqualTo(secondTitle);

        expectNotFound(
            mockMvcPerform(patch("/v1/readings/{id}", firstReading.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + secondClient.getId() + "\"}")),
            "Client not found.");
        assertThat(readingRepository.findById(firstReading.getId()).orElseThrow().getClientId())
            .isEqualTo(firstClient.getId());
    }

    @Test
    void shouldNotDeleteAnotherUsersReading() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);

        mockMvcPerform(delete("/v1/readings/{id}", firstReading.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isNoContent());
        assertThat(readingRepository.findById(firstReading.getId())).isEmpty();

        expectNotFound(
            mockMvcPerform(delete("/v1/readings/{id}", secondReading.getId()).with(authenticatedAs(firstUser))),
            "Reading not found.");
        assertThat(readingRepository.findById(secondReading.getId())).isPresent();
    }

    @Test
    void shouldNotUpdatePositionFromAnotherReadingOrOwner() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);
        ReadingPosition firstPosition = position(firstReading);
        ReadingPosition secondPosition = position(secondReading);

        mockMvcPerform(put("/v1/readings/{id}/positions/{positionId}",
                firstReading.getId(), firstPosition.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":\"the-fool\",\"orientation\":\"UPRIGHT\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.card.id").value("the-fool"))
            .andExpect(jsonPath("$.card.number").value(0))
            .andExpect(jsonPath("$.card.suit").value("major"))
            .andExpect(jsonPath("$.card.name").isNotEmpty());

        expectNotFound(
            mockMvcPerform(put("/v1/readings/{id}/positions/{positionId}",
                firstReading.getId(), secondPosition.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":\"the-fool\",\"orientation\":\"UPRIGHT\"}")),
            "Reading position not found.");
        expectNotFound(
            mockMvcPerform(put("/v1/readings/{id}/positions/{positionId}",
                secondReading.getId(), secondPosition.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":\"the-fool\",\"orientation\":\"UPRIGHT\"}")),
            "Reading not found.");
        assertThat(positionRepository.findById(secondPosition.getId()).orElseThrow().getCardId()).isNull();
    }

    @Test
    void shouldNotCompleteAnotherUsersReading() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);
        ReadingPosition firstPosition = position(firstReading);
        firstPosition.update(
            "the-fool",
            CardOrientation.UPRIGHT,
            null,
            java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        positionRepository.flush();

        mockMvcPerform(post("/v1/readings/{id}/complete", firstReading.getId())
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        expectNotFound(
            mockMvcPerform(post("/v1/readings/{id}/complete", secondReading.getId())
                .with(authenticatedAs(firstUser))),
            "Reading not found.");
        assertThat(readingRepository.findById(secondReading.getId()).orElseThrow().getStatus())
            .isEqualTo(ReadingStatus.IN_PROGRESS);
    }

    @Test
    void shouldNotArchiveAnotherUsersReading() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);

        mockMvcPerform(post("/v1/readings/{id}/archive", firstReading.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        expectNotFound(
            mockMvcPerform(post("/v1/readings/{id}/archive", secondReading.getId())
                .with(authenticatedAs(firstUser))),
            "Reading not found.");
        assertThat(readingRepository.findById(secondReading.getId()).orElseThrow().getArchivedAt()).isNull();
    }

    @Test
    void shouldNotRestoreAnotherUsersReading() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);
        firstReading.archive(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        secondReading.archive(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        readingRepository.flush();

        mockMvcPerform(post("/v1/readings/{id}/restore", firstReading.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isOk());

        expectNotFound(
            mockMvcPerform(post("/v1/readings/{id}/restore", secondReading.getId())
                .with(authenticatedAs(firstUser))),
            "Reading not found.");
        assertThat(readingRepository.findById(secondReading.getId()).orElseThrow().getArchivedAt()).isNotNull();
    }

    @Test
    void shouldListOnlyCommentsFromOwnedReading() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);
        ReadingComment firstComment = entityGeneratorService.randomComment(firstUser, firstReading);
        entityGeneratorService.randomComment(secondUser, secondReading);

        mockMvcPerform(get("/v1/readings/{id}/comments", firstReading.getId())
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(firstComment.getId().toString()));

        expectNotFound(
            mockMvcPerform(get("/v1/readings/{id}/comments", secondReading.getId())
                .with(authenticatedAs(firstUser))),
            "Reading not found.");
    }

    @Test
    void shouldCreateCommentOnlyForOwnedReading() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);

        String response = mockMvcPerform(post("/v1/readings/{id}/comments", firstReading.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Own comment\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.readingId").value(firstReading.getId().toString()))
            .andExpect(jsonPath("$.body").value("Own comment"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.updatedAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID commentId = UUID.fromString(JsonPath.read(response, "$.id"));
        assertThat(commentRepository.findById(commentId).orElseThrow().getOwnerId()).isEqualTo(firstUser.getId());

        long countBeforeForeignAttempt = commentRepository.count();
        expectNotFound(
            mockMvcPerform(post("/v1/readings/{id}/comments", secondReading.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Leaked comment\"}")),
            "Reading not found.");
        assertThat(commentRepository.count()).isEqualTo(countBeforeForeignAttempt);
    }

    @Test
    void shouldNotUpdateAnotherReadingsComment() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);
        ReadingComment firstComment = entityGeneratorService.randomComment(firstUser, firstReading);
        ReadingComment secondComment = entityGeneratorService.randomComment(secondUser, secondReading);
        String secondBody = secondComment.getBody();

        mockMvcPerform(patch("/v1/readings/{id}/comments/{commentId}",
                firstReading.getId(), firstComment.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Updated own comment\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("Updated own comment"));

        expectNotFound(
            mockMvcPerform(patch("/v1/readings/{id}/comments/{commentId}",
                firstReading.getId(), secondComment.getId())
                .with(authenticatedAs(firstUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Leaked update\"}")),
            "Comment not found.");
        assertThat(commentRepository.findById(secondComment.getId()).orElseThrow().getBody())
            .isEqualTo(secondBody);
    }

    @Test
    void shouldNotDeleteAnotherReadingsComment() throws Exception {
        Reading firstReading = reading(firstUser);
        Reading secondReading = reading(secondUser);
        ReadingComment firstComment = entityGeneratorService.randomComment(firstUser, firstReading);
        ReadingComment secondComment = entityGeneratorService.randomComment(secondUser, secondReading);

        mockMvcPerform(delete("/v1/readings/{id}/comments/{commentId}",
                firstReading.getId(), firstComment.getId())
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isNoContent());
        assertThat(commentRepository.findById(firstComment.getId())).isEmpty();

        expectNotFound(
            mockMvcPerform(delete("/v1/readings/{id}/comments/{commentId}",
                firstReading.getId(), secondComment.getId())
                .with(authenticatedAs(firstUser))),
            "Comment not found.");
        assertThat(commentRepository.findById(secondComment.getId())).isPresent();
    }

    private Reading reading(Profile owner) {
        return reading(owner, null);
    }

    private Reading reading(Profile owner, Client client) {
        Reading reading = entityGeneratorService.randomReading(owner, client);
        reading.setSpreadId("advice");
        reading.setDeckMode(ReadingDeckMode.MAJOR);
        reading.setStatus(ReadingStatus.IN_PROGRESS);
        reading.setCompletedAt(null);
        readingRepository.flush();
        SpreadPosition spreadPosition = spreadPositionRepository
            .findAllBySpread_IdOrderByPositionOrderAsc(reading.getSpreadId())
            .getFirst();
        entityGeneratorService.randomReadingPosition(reading, spreadPosition);
        return reading;
    }

    private ReadingPosition position(Reading reading) {
        return positionRepository
            .findAllByReadingIdOrderByPositionOrderAsc(reading.getId())
            .getFirst();
    }
}
