package com.arkana.service;

import com.arkana.domain.Profile;
import com.arkana.domain.Reading;
import com.arkana.domain.ReadingPosition;
import com.arkana.domain.ReadingShare;
import com.arkana.domain.ReadingShareStatus;
import com.arkana.domain.ReadingStatus;
import com.arkana.domain.Spread;
import com.arkana.domain.TarotCard;
import com.arkana.dto.reading.ReadingShareResponse;
import com.arkana.dto.reading.ReadingSpreadSummaryResponse;
import com.arkana.dto.reading.SharedReadingPositionResponse;
import com.arkana.dto.reading.SharedReadingResponse;
import com.arkana.dto.reading.SharedTarotCardResponse;
import com.arkana.repository.ProfileRepository;
import com.arkana.repository.ReadingPositionRepository;
import com.arkana.repository.ReadingRepository;
import com.arkana.repository.ReadingShareRepository;
import com.arkana.repository.SpreadRepository;
import com.arkana.repository.TarotCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReadingShareService {
  private static final int EXPIRATION_DAYS = 30;

  private final ReadingShareRepository shares;
  private final ReadingRepository readings;
  private final ReadingPositionRepository positions;
  private final SpreadRepository spreads;
  private final TarotCardRepository cards;
  private final ProfileRepository profiles;
  private final ProductAccessAuthorizer access;

  @Transactional
  public ReadingShareResponse create(UUID ownerId, UUID readingId) {
    access.requireAccess(ownerId);
    Reading reading = ownedReadingForUpdate(ownerId, readingId);
    if (reading.getStatus() != ReadingStatus.COMPLETED) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Only completed readings can be shared.");
    }

    OffsetDateTime currentTime = now();
    Optional<ReadingShare> current = shares.findFirstByReading_IdAndStatus(
        readingId,
        ReadingShareStatus.ACTIVE);
    if (current.isPresent() && current.get().getExpiresAt().isAfter(currentTime)) {
      return response(current.get());
    }
    current.ifPresent(ReadingShare::expire);
    shares.flush();

    ReadingShare created = shares.saveAndFlush(ReadingShare.builder()
        .id(UUID.randomUUID())
        .reading(reading)
        .status(ReadingShareStatus.ACTIVE)
        .createdAt(currentTime)
        .expiresAt(currentTime.plusDays(EXPIRATION_DAYS))
        .accessCount(0)
        .build());
    return response(created);
  }

  @Transactional
  public void cancel(UUID ownerId, UUID readingId) {
    access.requireAccess(ownerId);
    ownedReadingForUpdate(ownerId, readingId);
    OffsetDateTime currentTime = now();
    shares.findFirstByReading_IdAndStatus(readingId, ReadingShareStatus.ACTIVE)
        .ifPresent(share -> {
          if (share.getExpiresAt().isAfter(currentTime)) {
            share.cancel();
          } else {
            share.expire();
          }
        });
  }

  @Transactional
  public Optional<SharedReadingResponse> publicReading(UUID shareId) {
    OffsetDateTime currentTime = now();
    int updated = shares.incrementAccessCount(
        shareId,
        ReadingShareStatus.ACTIVE,
        currentTime);
    if (updated == 0) {
      shares.findById(shareId)
          .filter(share -> share.getStatus() == ReadingShareStatus.ACTIVE)
          .filter(share -> !share.getExpiresAt().isAfter(currentTime))
          .ifPresent(ReadingShare::expire);
      return Optional.empty();
    }

    ReadingShare share = shares.findById(shareId).orElseThrow();
    Reading reading = share.getReading();
    Spread spread = spreads.findById(reading.getSpreadId()).orElseThrow();
    Profile reader = profiles.findById(reading.getOwnerId()).orElseThrow();
    List<ReadingPosition> readingPositions =
        positions.findAllByReadingIdOrderByPositionOrderAsc(reading.getId());
    Map<String, TarotCard> cardsById = cards.findAllById(readingPositions.stream()
            .map(ReadingPosition::getCardId)
            .filter(java.util.Objects::nonNull)
            .toList())
        .stream()
        .collect(Collectors.toMap(TarotCard::getId, Function.identity()));

    return Optional.of(new SharedReadingResponse(
        share.getId(),
        reading.getTitle(),
        reading.getQuestion(),
        new ReadingSpreadSummaryResponse(spread.getId(), spread.getNamePtBr()),
        reading.getDeckMode().name(),
        reading.getCompletedAt(),
        reader.getDisplayName(),
        readingPositions.stream()
            .map(position -> position(position, cardsById.get(position.getCardId())))
            .toList()));
  }

  private SharedReadingPositionResponse position(ReadingPosition position, TarotCard card) {
    SharedTarotCardResponse cardResponse = card == null
        ? null
        : new SharedTarotCardResponse(
            card.getId(),
            card.getCardNumber(),
            card.getSuit(),
            card.getNamePtBr());
    return new SharedReadingPositionResponse(
        position.getPositionKey(),
        position.getPositionOrder(),
        position.getNamePtBr(),
        position.getMeaningPtBr(),
        position.getX(),
        position.getY(),
        position.getRotation(),
        cardResponse,
        position.getOrientation(),
        position.getInterpretation());
  }

  private Reading ownedReadingForUpdate(UUID ownerId, UUID readingId) {
    return readings.findByIdAndOwnerIdForUpdate(readingId, ownerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reading not found."));
  }

  private ReadingShareResponse response(ReadingShare share) {
    return new ReadingShareResponse(share.getId(), share.getReading().getId(), share.getCreatedAt());
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }
}
