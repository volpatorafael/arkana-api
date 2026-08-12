package com.arkana.controller;

import com.arkana.domain.Client;
import com.arkana.domain.Profile;
import com.arkana.domain.Reading;
import com.arkana.domain.ReadingDeckMode;
import com.arkana.domain.ReadingPosition;
import com.arkana.domain.ReadingShare;
import com.arkana.domain.ReadingShareStatus;
import com.arkana.domain.ReadingStatus;
import com.arkana.domain.SpreadPosition;
import com.arkana.repository.ProfileRepository;
import com.arkana.repository.ReadingCommentRepository;
import com.arkana.repository.ReadingPositionRepository;
import com.arkana.repository.ReadingRepository;
import com.arkana.repository.ReadingShareRepository;
import com.arkana.repository.SpreadPositionRepository;
import com.arkana.service.BillingService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadingShareControllerIT extends BaseControllerIT {
    @Autowired
    private ReadingShareRepository shareRepository;
    @Autowired
    private ReadingRepository readingRepository;
    @Autowired
    private ReadingCommentRepository commentRepository;
    @Autowired
    private ReadingPositionRepository positionRepository;
    @Autowired
    private SpreadPositionRepository spreadPositionRepository;
    @Autowired
    private ProfileRepository profileRepository;
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
    void shouldCreateOneShareAndReturnItInReadingResponses() throws Exception {
        Reading reading = completedReading(firstUser);
        OffsetDateTime beforeCreation = now();

        String firstResponse = mockMvcPerform(post("/v1/readings/{id}/share", reading.getId())
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readingId").value(reading.getId().toString()))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UUID shareId = UUID.fromString(JsonPath.read(firstResponse, "$.id"));

        mockMvcPerform(post("/v1/readings/{id}/share", reading.getId())
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(shareId.toString()));

        ReadingShare persisted = shareRepository.findById(shareId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ReadingShareStatus.ACTIVE);
        assertThat(persisted.getAccessCount()).isZero();
        assertThat(persisted.getCreatedAt()).isBetween(beforeCreation, now());
        assertThat(persisted.getExpiresAt()).isBetween(
            beforeCreation.plusDays(30),
            now().plusDays(30));
        assertThat(shareRepository.countByReading_IdAndStatus(reading.getId(), ReadingShareStatus.ACTIVE))
            .isEqualTo(1);

        mockMvcPerform(get("/v1/readings/{id}", reading.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readingShareId").value(shareId.toString()));
        mockMvcPerform(get("/v1/readings").with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].readingShareId").value(shareId.toString()));
    }

    @Test
    void shouldRejectForeignAndInProgressReadings() throws Exception {
        Reading foreignReading = completedReading(secondUser);
        Reading inProgress = inProgressReading(firstUser);
        Profile blockedUser = entityGeneratorService.randomProfile();
        Reading blockedReading = completedReading(blockedUser);
        long shareCountBeforeAttempts = shareRepository.count();

        expectNotFound(
            mockMvcPerform(post("/v1/readings/{id}/share", foreignReading.getId())
                .with(authenticatedAs(firstUser))),
            "Reading not found.");
        mockMvcPerform(post("/v1/readings/{id}/share", inProgress.getId())
                .with(authenticatedAs(firstUser)))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value("Only completed readings can be shared."));
        mockMvcPerform(post("/v1/readings/{id}/share", blockedReading.getId())
                .with(authenticatedAs(blockedUser)))
            .andExpect(status().isForbidden());
        assertThat(shareRepository.count()).isEqualTo(shareCountBeforeAttempts);
    }

    @Test
    void shouldReturnPublicReadingAndIncrementAccessCountWithoutPrivateData() throws Exception {
        firstUser.setDisplayName("Tarologa publica");
        profileRepository.saveAndFlush(firstUser);
        Reading reading = completedReading(firstUser);
        Client client = entityGeneratorService.randomClient(firstUser);
        reading.setClientId(client.getId());
        reading.setTitle("Leitura compartilhada");
        reading.setQuestion("Qual caminho seguir?");
        reading.setContext("Contexto privado");
        reading.setConsultationFeeAmount(18000);
        reading.setConsultationDurationMinutes(60);
        reading.archive(now());
        readingRepository.saveAndFlush(reading);
        var comment = entityGeneratorService.randomComment(firstUser, reading);
        comment.setBody("Comentario publico");
        comment = commentRepository.saveAndFlush(comment);
        ReadingShare share = activeShare(reading);

        for (int access = 1; access <= 2; access++) {
            mockMvcPerform(get("/v1/public/reading-shares/{id}", share.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(share.getId().toString()))
                .andExpect(jsonPath("$.title").value("Leitura compartilhada"))
                .andExpect(jsonPath("$.question").value("Qual caminho seguir?"))
                .andExpect(jsonPath("$.readerDisplayName").value("Tarologa publica"))
                .andExpect(jsonPath("$.spread.id").value("advice"))
                .andExpect(jsonPath("$.spread.name").isNotEmpty())
                .andExpect(jsonPath("$.deckMode").value("MAJOR"))
                .andExpect(jsonPath("$.startedAt").value(reading.getStartedAt().toString()))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.positions", hasSize(1)))
                .andExpect(jsonPath("$.positions[0].key").isNotEmpty())
                .andExpect(jsonPath("$.positions[0].order").isNumber())
                .andExpect(jsonPath("$.positions[0].name").isNotEmpty())
                .andExpect(jsonPath("$.positions[0].meaning").isNotEmpty())
                .andExpect(jsonPath("$.positions[0].x").isNumber())
                .andExpect(jsonPath("$.positions[0].y").isNumber())
                .andExpect(jsonPath("$.positions[0].rotation").isNumber())
                .andExpect(jsonPath("$.positions[0].card.id").value("the-fool"))
                .andExpect(jsonPath("$.positions[0].card.number").value(0))
                .andExpect(jsonPath("$.positions[0].card.suit").value("major"))
                .andExpect(jsonPath("$.positions[0].card.name").isNotEmpty())
                .andExpect(jsonPath("$.positions[0].orientation").value("UPRIGHT"))
                .andExpect(jsonPath("$.positions[0].interpretation").value("Interpretacao publica"))
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.readingId").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.context").doesNotExist())
                .andExpect(jsonPath("$.consultationFeeAmount").doesNotExist())
                .andExpect(jsonPath("$.consultationFeeCurrency").doesNotExist())
                .andExpect(jsonPath("$.consultationDurationMinutes").doesNotExist())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].id").value(comment.getId().toString()))
                .andExpect(jsonPath("$.comments[0].body").value("Comentario publico"))
                .andExpect(jsonPath("$.comments[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.comments[0].readingId").doesNotExist())
                .andExpect(jsonPath("$.comments[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$.comments[0].updatedAt").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
        }

        assertThat(shareRepository.findById(share.getId()).orElseThrow().getAccessCount()).isEqualTo(2);
    }

    @Test
    void shouldReturnTheSameNotFoundForMissingExpiredAndCanceledShares() throws Exception {
        Reading expiredReading = completedReading(firstUser);
        Reading canceledReading = completedReading(firstUser);
        ReadingShare expired = share(expiredReading, ReadingShareStatus.ACTIVE, now().minusDays(1));
        ReadingShare canceled = share(canceledReading, ReadingShareStatus.CANCELED, now().plusDays(1));

        for (UUID shareId : List.of(UUID.randomUUID(), expired.getId(), canceled.getId())) {
            expectNotFound(
                mockMvcPerform(get("/v1/public/reading-shares/{id}", shareId)),
                "Reading share not found.");
        }

        assertThat(shareRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(ReadingShareStatus.EXPIRED);
        assertThat(shareRepository.findById(expired.getId()).orElseThrow().getAccessCount()).isZero();
        assertThat(shareRepository.findById(canceled.getId()).orElseThrow().getAccessCount()).isZero();
    }

    @Test
    void shouldCancelOnlyTheOwnersShareAndRemainIdempotent() throws Exception {
        Reading reading = completedReading(firstUser);
        ReadingShare share = activeShare(reading);

        expectNotFound(
            mockMvcPerform(delete("/v1/readings/{id}/share", reading.getId())
                .with(authenticatedAs(secondUser))),
            "Reading not found.");
        assertThat(shareRepository.findById(share.getId()).orElseThrow().getStatus())
            .isEqualTo(ReadingShareStatus.ACTIVE);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvcPerform(delete("/v1/readings/{id}/share", reading.getId())
                    .with(authenticatedAs(firstUser)))
                .andExpect(status().isNoContent());
        }
        assertThat(shareRepository.findById(share.getId()).orElseThrow().getStatus())
            .isEqualTo(ReadingShareStatus.CANCELED);
        mockMvcPerform(get("/v1/readings/{id}", reading.getId()).with(authenticatedAs(firstUser)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readingShareId").isEmpty());
        expectNotFound(
            mockMvcPerform(get("/v1/public/reading-shares/{id}", share.getId())),
            "Reading share not found.");
    }

    @Test
    void shouldCreateNewIdsAfterExpirationAndCancellation() throws Exception {
        Reading expiredReading = completedReading(firstUser);
        Reading canceledReading = completedReading(firstUser);
        ReadingShare expired = share(expiredReading, ReadingShareStatus.ACTIVE, now().minusDays(1));
        ReadingShare canceled = share(canceledReading, ReadingShareStatus.CANCELED, now().plusDays(1));

        UUID replacementForExpired = createShare(firstUser, expiredReading);
        UUID replacementForCanceled = createShare(firstUser, canceledReading);

        assertThat(replacementForExpired).isNotEqualTo(expired.getId());
        assertThat(replacementForCanceled).isNotEqualTo(canceled.getId());
        assertThat(shareRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(ReadingShareStatus.EXPIRED);
        assertThat(shareRepository.findById(canceled.getId()).orElseThrow().getStatus())
            .isEqualTo(ReadingShareStatus.CANCELED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldSerializeConcurrentCreationAndCountConcurrentPublicReads() throws Exception {
        Reading reading = completedReading(firstUser);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<UUID>> createRequests = List.of(
                () -> concurrentCreate(start, reading),
                () -> concurrentCreate(start, reading));
            List<Future<UUID>> created = new ArrayList<>();
            for (Callable<UUID> request : createRequests) {
                created.add(executor.submit(request));
            }
            start.countDown();
            UUID firstId = created.get(0).get();
            UUID secondId = created.get(1).get();
            assertThat(firstId).isEqualTo(secondId);
            assertThat(shareRepository.countByReading_IdAndStatus(reading.getId(), ReadingShareStatus.ACTIVE))
                .isEqualTo(1);

            int accesses = 12;
            CountDownLatch readStart = new CountDownLatch(1);
            List<Future<Integer>> reads = new ArrayList<>();
            for (int index = 0; index < accesses; index++) {
                reads.add(executor.submit(() -> {
                    readStart.await();
                    return mockMvcPerform(get("/v1/public/reading-shares/{id}", firstId))
                        .andReturn()
                        .getResponse()
                        .getStatus();
                }));
            }
            readStart.countDown();
            for (Future<Integer> read : reads) {
                assertThat(read.get()).isEqualTo(200);
            }
            assertThat(shareRepository.findById(firstId).orElseThrow().getAccessCount())
                .isEqualTo(accesses);
        } finally {
            executor.shutdownNow();
        }
    }

    private UUID concurrentCreate(CountDownLatch start, Reading reading) throws Exception {
        start.await();
        String response = mockMvcPerform(post("/v1/readings/{id}/share", reading.getId())
                .with(authenticatedAs(firstUser)))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private UUID createShare(Profile owner, Reading reading) throws Exception {
        String response = mockMvcPerform(post("/v1/readings/{id}/share", reading.getId())
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private Reading completedReading(Profile owner) {
        Reading reading = inProgressReading(owner);
        reading.setStatus(ReadingStatus.COMPLETED);
        reading.setCompletedAt(now());
        reading = readingRepository.saveAndFlush(reading);
        ReadingPosition position = positionRepository
            .findAllByReadingIdOrderByPositionOrderAsc(reading.getId())
            .getFirst();
        position.update("the-fool", "UPRIGHT", "Interpretacao publica", now());
        positionRepository.saveAndFlush(position);
        return reading;
    }

    private Reading inProgressReading(Profile owner) {
        Reading reading = entityGeneratorService.randomReading(owner);
        reading.setSpreadId("advice");
        reading.setDeckMode(ReadingDeckMode.MAJOR);
        reading.setStatus(ReadingStatus.IN_PROGRESS);
        reading.setCompletedAt(null);
        reading = readingRepository.saveAndFlush(reading);
        SpreadPosition spreadPosition = spreadPositionRepository
            .findAllBySpread_IdOrderByPositionOrderAsc("advice")
            .getFirst();
        entityGeneratorService.randomReadingPosition(reading, spreadPosition);
        return reading;
    }

    private ReadingShare activeShare(Reading reading) {
        return share(reading, ReadingShareStatus.ACTIVE, now().plusDays(30));
    }

    private ReadingShare share(
        Reading reading,
        ReadingShareStatus status,
        OffsetDateTime expiresAt) {
        ReadingShare share = entityGeneratorService.randomReadingShare(reading);
        share.setStatus(status);
        share.setCreatedAt(expiresAt.minusDays(30));
        share.setExpiresAt(expiresAt);
        share.setAccessCount(0);
        return shareRepository.saveAndFlush(share);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
