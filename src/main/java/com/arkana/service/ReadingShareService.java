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
import com.arkana.dto.reading.SharedReadingResponse;
import com.arkana.mapper.ReadingShareMapper;
import com.arkana.mapper.ReadingPositionMappingSource;
import com.arkana.repository.ProfileRepository;
import com.arkana.repository.ReadingCommentRepository;
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
  private final ReadingCommentRepository comments;
  private final ReadingPositionRepository positions;
  private final SpreadRepository spreads;
  private final TarotCardRepository cards;
  private final ProfileRepository profiles;
  private final ProductAccessAuthorizer access;
  private final ReadingShareMapper mapper;

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
      return mapper.toResponse(current.get());
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
    return mapper.toResponse(created);
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
    List<TarotCard> readingCards = cards.findAllById(readingPositions.stream()
            .map(ReadingPosition::getCardId)
            .filter(java.util.Objects::nonNull)
            .toList());
    Map<String, TarotCard> cardsById = readingCards.stream()
        .collect(Collectors.toMap(TarotCard::getId, Function.identity()));
    List<ReadingPositionMappingSource> positionSources = readingPositions.stream()
        .map(position -> new ReadingPositionMappingSource(
            position,
            cardsById.get(position.getCardId())))
        .toList();
    return Optional.of(mapper.toSharedResponse(
        share,
        spread,
        reader,
        positionSources,
        comments.findAllByReadingIdAndOwnerIdOrderByCreatedAtAscIdAsc(
            reading.getId(),
            reading.getOwnerId())));
  }

  private Reading ownedReadingForUpdate(UUID ownerId, UUID readingId) {
    return readings.findByIdAndOwnerIdForUpdate(readingId, ownerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reading not found."));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }
}
