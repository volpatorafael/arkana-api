package com.arkana.controller;

import com.arkana.domain.CardOrientation;
import com.arkana.domain.Profile;
import com.arkana.domain.Reading;
import com.arkana.domain.ReadingDeckMode;
import com.arkana.domain.ReadingPosition;
import com.arkana.domain.ReadingStatus;
import com.arkana.repository.ReadingPositionRepository;
import com.arkana.repository.ReadingRepository;
import com.arkana.service.BillingService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadingFreeFormControllerIT extends BaseControllerIT {

    @Autowired
    private ReadingRepository readingRepository;
    @Autowired
    private ReadingPositionRepository positionRepository;
    @Autowired
    private BillingService billingService;
    @Autowired
    private EntityManager entityManager;

    private Profile owner;

    @BeforeEach
    void setUpOwner() {
        owner = entityGeneratorService.randomProfile();
        billingService.startTrial(owner.getId());
    }

    @Test
    void shouldPersistTheWholeFreeformReadingFlowAndExposeItsCurrentSharedLayout() throws Exception {
        String createdResponse = mockMvcPerform(post("/v1/readings")
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"spreadId\":\"free\",\"deckMode\":\"MAJOR\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.spread.kind").value("FREEFORM"))
            .andExpect(jsonPath("$.positions", hasSize(0)))
            .andReturn().getResponse().getContentAsString();
        UUID readingId = uuidFrom(createdResponse, "$.id");

        clearPersistenceContext();
        Reading persistedReading = readingRepository.findById(readingId).orElseThrow();
        assertThat(persistedReading.getOwnerId()).isEqualTo(owner.getId());
        assertThat(persistedReading.getSpreadId()).isEqualTo("free");
        assertThat(persistedReading.getDeckMode()).isEqualTo(ReadingDeckMode.MAJOR);
        assertThat(persistedReading.getStatus()).isEqualTo(ReadingStatus.IN_PROGRESS);
        assertThat(positionRepository.findAllByReadingIdOrderByPositionOrderAsc(readingId)).isEmpty();

        String foolResponse = addCard(readingId, "the-fool", CardOrientation.UPRIGHT)
            .andExpect(jsonPath("$.name").value("Carta 1"))
            .andExpect(jsonPath("$.stackOrder").value(1))
            .andReturn().getResponse().getContentAsString();
        UUID foolPositionId = uuidFrom(foolResponse, "$.id");

        String magicianResponse = addCard(readingId, "the-magician", CardOrientation.REVERSED)
            .andExpect(jsonPath("$.name").value("Carta 2"))
            .andExpect(jsonPath("$.stackOrder").value(2))
            .andReturn().getResponse().getContentAsString();
        UUID magicianPositionId = uuidFrom(magicianResponse, "$.id");

        clearPersistenceContext();
        List<ReadingPosition> initialPositions = positions(readingId);
        assertThat(initialPositions).hasSize(2);
        assertPosition(initialPositions.get(0), foolPositionId, (short) 1, "the-fool",
            CardOrientation.UPRIGHT, "50.00", "50.00", (short) 0, 1);
        assertPosition(initialPositions.get(1), magicianPositionId, (short) 2, "the-magician",
            CardOrientation.REVERSED, "50.00", "50.00", (short) 0, 2);

        mockMvcPerform(patch("/v1/readings/{id}/positions/{positionId}", readingId, foolPositionId)
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"x\":12.5,\"y\":87.5,\"rotation\":45}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stackOrder").value(3));

        clearPersistenceContext();
        ReadingPosition movedFool = positionRepository.findById(foolPositionId).orElseThrow();
        assertPosition(movedFool, foolPositionId, (short) 1, "the-fool",
            CardOrientation.UPRIGHT, "12.50", "87.50", (short) 45, 3);

        mockMvcPerform(delete("/v1/readings/{id}/positions/{positionId}", readingId, foolPositionId)
                .with(authenticatedAs(owner)))
            .andExpect(status().isNoContent());

        clearPersistenceContext();
        assertThat(positionRepository.findById(foolPositionId)).isEmpty();
        assertThat(positions(readingId))
            .singleElement()
            .satisfies(position -> assertThat(position.getPositionOrder()).isEqualTo((short) 2));

        mockMvcPerform(post("/v1/readings/{id}/complete", readingId)
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        clearPersistenceContext();
        assertThat(readingRepository.findById(readingId).orElseThrow().getStatus())
            .isEqualTo(ReadingStatus.COMPLETED);

        String priestessResponse = addCard(readingId, "the-high-priestess", CardOrientation.REVERSED)
            .andExpect(jsonPath("$.name").value("Carta 1"))
            .andExpect(jsonPath("$.stackOrder").value(3))
            .andReturn().getResponse().getContentAsString();
        UUID priestessPositionId = uuidFrom(priestessResponse, "$.id");

        mockMvcPerform(patch("/v1/readings/{id}/positions/{positionId}", readingId, priestessPositionId)
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"x\":75,\"y\":25,\"rotation\":-45}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stackOrder").value(4));

        mockMvcPerform(delete("/v1/readings/{id}/positions/{positionId}", readingId, magicianPositionId)
                .with(authenticatedAs(owner)))
            .andExpect(status().isNoContent());

        clearPersistenceContext();
        assertThat(readingRepository.findById(readingId).orElseThrow().getStatus())
            .isEqualTo(ReadingStatus.COMPLETED);
        assertThat(positions(readingId))
            .singleElement()
            .satisfies(position -> assertPosition(position, priestessPositionId, (short) 1,
                "the-high-priestess", CardOrientation.REVERSED,
                "75.00", "25.00", (short) -45, 4));

        String shareResponse = mockMvcPerform(post("/v1/readings/{id}/share", readingId)
                .with(authenticatedAs(owner)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        UUID shareId = uuidFrom(shareResponse, "$.id");

        mockMvcPerform(get("/v1/public/reading-shares/{id}", shareId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spread.id").value("free"))
            .andExpect(jsonPath("$.spread.kind").value("FREEFORM"))
            .andExpect(jsonPath("$.positions", hasSize(1)))
            .andExpect(jsonPath("$.positions[0].name").value("Carta 1"))
            .andExpect(jsonPath("$.positions[0].card.id").value("the-high-priestess"))
            .andExpect(jsonPath("$.positions[0].orientation").value("REVERSED"))
            .andExpect(jsonPath("$.positions[0].x").value(75.0))
            .andExpect(jsonPath("$.positions[0].y").value(25.0))
            .andExpect(jsonPath("$.positions[0].rotation").value(-45))
            .andExpect(jsonPath("$.positions[0].stackOrder").value(4));

        mockMvcPerform(delete("/v1/readings/{id}/positions/{positionId}", readingId, priestessPositionId)
                .with(authenticatedAs(owner)))
            .andExpect(status().isNoContent());

        clearPersistenceContext();
        assertThat(positionRepository.findAllByReadingIdOrderByPositionOrderAsc(readingId)).isEmpty();
        assertThat(readingRepository.findById(readingId).orElseThrow().getStatus())
            .isEqualTo(ReadingStatus.COMPLETED);
        mockMvcPerform(get("/v1/public/reading-shares/{id}", shareId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.positions", hasSize(0)));
    }

    private org.springframework.test.web.servlet.ResultActions addCard(
        UUID readingId,
        String cardId,
        CardOrientation orientation
    ) throws Exception {
        return mockMvcPerform(post("/v1/readings/{id}/positions", readingId)
                .with(authenticatedAs(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":\"" + cardId + "\",\"orientation\":\""
                    + orientation.name() + "\"}"))
            .andExpect(status().isCreated());
    }

    private List<ReadingPosition> positions(UUID readingId) {
        return positionRepository.findAllByReadingIdOrderByPositionOrderAsc(readingId);
    }

    private void assertPosition(
        ReadingPosition position,
        UUID id,
        short positionOrder,
        String cardId,
        CardOrientation orientation,
        String x,
        String y,
        short rotation,
        int stackOrder
    ) {
        assertThat(position.getId()).isEqualTo(id);
        assertThat(position.getSpreadPositionId()).isNull();
        assertThat(position.getPositionOrder()).isEqualTo(positionOrder);
        assertThat(position.getPositionKey()).isEqualTo("card-" + positionOrder);
        assertThat(position.getNamePtBr()).isEqualTo("Carta " + positionOrder);
        assertThat(position.getNameEn()).isEqualTo("Card " + positionOrder);
        assertThat(position.getCardId()).isEqualTo(cardId);
        assertThat(position.getOrientation()).isEqualTo(orientation);
        assertThat(position.getX()).isEqualByComparingTo(new BigDecimal(x));
        assertThat(position.getY()).isEqualByComparingTo(new BigDecimal(y));
        assertThat(position.getRotation()).isEqualTo(rotation);
        assertThat(position.getStackOrder()).isEqualTo(stackOrder);
    }

    private UUID uuidFrom(String json, String path) {
        return UUID.fromString(JsonPath.read(json, path));
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
