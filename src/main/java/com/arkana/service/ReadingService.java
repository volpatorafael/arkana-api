package com.arkana.service;

import com.arkana.domain.Reading;
import com.arkana.domain.ReadingComment;
import com.arkana.domain.ReadingDeckMode;
import com.arkana.domain.ReadingPosition;
import com.arkana.domain.ReadingShare;
import com.arkana.domain.ReadingShareStatus;
import com.arkana.domain.ReadingStatus;
import com.arkana.domain.Spread;
import com.arkana.domain.TarotCard;
import com.arkana.dto.reading.CreateReadingRequest;
import com.arkana.dto.reading.ReadingCommentResponse;
import com.arkana.dto.reading.ReadingPageResponse;
import com.arkana.dto.reading.ReadingPositionResponse;
import com.arkana.dto.reading.ReadingResponse;
import com.arkana.dto.reading.SaveReadingCommentRequest;
import com.arkana.dto.reading.SaveReadingPositionRequest;
import com.arkana.dto.reading.UpdateReadingRequest;
import com.arkana.mapper.ReadingMapper;
import com.arkana.mapper.ReadingCommentMapper;
import com.arkana.mapper.ReadingPositionMapper;
import com.arkana.mapper.SpreadMapper;
import com.arkana.repository.ClientRepository;
import com.arkana.repository.ReadingCommentRepository;
import com.arkana.repository.ReadingPositionRepository;
import com.arkana.repository.ReadingRepository;
import com.arkana.repository.ReadingShareRepository;
import com.arkana.repository.SpreadPositionRepository;
import com.arkana.repository.SpreadRepository;
import com.arkana.repository.TarotCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReadingService {
  private final ReadingRepository readings;
  private final ReadingPositionRepository positions;
  private final ReadingCommentRepository comments;
  private final ReadingShareRepository shares;
  private final SpreadRepository spreads;
  private final SpreadPositionRepository spreadPositions;
  private final TarotCardRepository cards;
  private final ClientRepository clients;
  private final ProductAccessAuthorizer access;
  private final ReadingMapper mapper;
  private final ReadingPositionMapper positionMapper;
  private final ReadingCommentMapper commentMapper;
  private final SpreadMapper spreadMapper;

  private static String trim(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private static ResponseStatusException badRequest(String detail) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
  }

  private static ResponseStatusException notFound(String detail) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
  }

  @Transactional
  public ReadingResponse create(UUID owner, CreateReadingRequest request, String locale) {
    access.requireAccess(owner);
    ReadingDeckMode deckMode = deckMode(request.deckMode());
    requireSpread(request.spreadId());
    requireClient(owner, request.clientId());

    OffsetDateTime createdAt = now();
    Reading reading = readings.save(Reading.builder()
        .id(UUID.randomUUID())
        .ownerId(owner)
        .clientId(request.clientId())
        .spreadId(request.spreadId())
        .deckMode(deckMode)
        .status(ReadingStatus.IN_PROGRESS)
        .title(trim(request.title()))
        .question(trim(request.question()))
        .context(trim(request.context()))
        .startedAt(createdAt)
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build());
    copySpreadPositions(reading.getId(), request.spreadId(), createdAt);
    return response(reading, locale);
  }

  @Transactional(readOnly = true)
  public ReadingResponse get(UUID owner, UUID id, String locale) {
    access.requireAccess(owner);
    return response(reading(owner, id), locale);
  }

  @Transactional(readOnly = true)
  public ReadingPageResponse list(
      UUID owner,
      int page,
      int pageSize,
      boolean archived,
      UUID clientId,
      String status,
      OffsetDateTime from,
      OffsetDateTime to) {
    access.requireAccess(owner);
    if (page < 1 || pageSize < 1 || pageSize > 100) {
      throw badRequest("Invalid pagination.");
    }
    ReadingStatus readingStatus = status == null ? null : readingStatus(status);

    Specification<Reading> specification = (root, query, builder) ->
        builder.equal(root.get("ownerId"), owner);
    specification = specification.and((root, query, builder) -> archived
        ? builder.isNotNull(root.get("archivedAt"))
        : builder.isNull(root.get("archivedAt")));
    if (clientId != null) {
      specification = specification.and((root, query, builder) ->
          builder.equal(root.get("clientId"), clientId));
    }
    if (readingStatus != null) {
      specification = specification.and((root, query, builder) ->
          builder.equal(root.get("status"), readingStatus));
    }
    if (from != null) {
      specification = specification.and((root, query, builder) ->
          builder.greaterThanOrEqualTo(root.get("startedAt"), from));
    }
    if (to != null) {
      specification = specification.and((root, query, builder) ->
          builder.lessThanOrEqualTo(root.get("startedAt"), to));
    }

    Pageable pageable = PageRequest.of(
        page - 1,
        pageSize,
        Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")));
    Page<Reading> result = readings.findAll(specification, pageable);
    Map<UUID, UUID> shareIds = activeShareIds(result.getContent(), now());
    return mapper.toPage(
        result.getContent().stream()
            .map(reading -> mapper.toSummary(reading, shareIds.get(reading.getId())))
            .toList(),
        page,
        pageSize,
        result.getTotalElements());
  }

  @Transactional
  public ReadingResponse update(UUID owner, UUID id, UpdateReadingRequest request, String locale) {
    access.requireAccess(owner);
    Reading reading = reading(owner, id);
    if (!request.any()) {
      throw badRequest("At least one field must be provided.");
    }
    if ((request.spreadPresent() || request.deckPresent() || request.clientPresent())
        && reading.getStatus() != ReadingStatus.IN_PROGRESS) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only metadata can be edited after completion.");
    }
    if (request.startedAtPresent() && request.startedAt() == null) {
      throw badRequest("startedAt cannot be null.");
    }
    ReadingDeckMode deckMode = reading.getDeckMode();
    if (request.deckPresent()) {
      if (request.deckMode() == null) {
        throw badRequest("deckMode cannot be null.");
      }
      deckMode = deckMode(request.deckMode());
    }
    if (request.clientPresent()) {
      requireClient(owner, request.clientId());
    }
    if (request.spreadPresent()) {
      updateSpread(reading, request.spreadId());
    }
    reading.update(
        request.clientPresent(),
        request.clientId(),
        request.spreadPresent(),
        request.spreadId(),
        request.deckPresent(),
        deckMode,
        request.titlePresent(),
        trim(request.title()),
        request.questionPresent(),
        trim(request.question()),
        request.contextPresent(),
        trim(request.context()),
        request.startedAtPresent(),
        request.startedAt(),
        now());
    return response(reading, locale);
  }

  @Transactional
  public ReadingPositionResponse savePosition(
      UUID owner,
      UUID readingId,
      UUID positionId,
      SaveReadingPositionRequest request,
      String locale) {
    access.requireAccess(owner);
    Reading reading = reading(owner, readingId);
    if ((request.cardId() == null) != (request.orientation() == null)) {
      throw badRequest("cardId and orientation must both be set or cleared.");
    }
    if (request.orientation() != null
        && !List.of("UPRIGHT", "REVERSED").contains(request.orientation())) {
      throw badRequest("Invalid orientation.");
    }

    ReadingPosition position = positions.findByIdAndReadingId(positionId, readingId)
        .orElseThrow(() -> notFound("Reading position not found."));
    boolean cardChanging = !Objects.equals(request.cardId(), position.getCardId())
        || !Objects.equals(request.orientation(), position.getOrientation());
    if (cardChanging && reading.getStatus() != ReadingStatus.IN_PROGRESS) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Card assignment is locked after completion.");
    }
    validateCardForDeck(reading, request.cardId());
    if (request.cardId() != null
        && positions.existsByReadingIdAndCardIdAndIdNot(readingId, request.cardId(), positionId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A card can only be used once per reading.");
    }
    position.update(request.cardId(), request.orientation(), trim(request.interpretation()), now());
    try {
      positions.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A card can only be used once per reading.");
    }
    TarotCard card = position.getCardId() == null
        ? null
        : cards.findById(position.getCardId()).orElse(null);
    return positionMapper.toResponse(position, card, locale);
  }

  @Transactional
  public ReadingResponse complete(UUID owner, UUID id, String locale) {
    access.requireAccess(owner);
    Reading reading = requireInProgress(owner, id);
    long incomplete = positions.countByReadingIdAndCardIdIsNull(id);
    if (incomplete > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Every position must have a card before completion.");
    }
    reading.complete(now());
    return response(reading, locale);
  }

  @Transactional
  public ReadingResponse archive(UUID owner, UUID id, String locale) {
    access.requireAccess(owner);
    Reading reading = reading(owner, id);
    reading.archive(now());
    return response(reading, locale);
  }

  @Transactional
  public ReadingResponse restore(UUID owner, UUID id, String locale) {
    access.requireAccess(owner);
    Reading reading = reading(owner, id);
    reading.restore(now());
    return response(reading, locale);
  }

  @Transactional
  public void delete(UUID owner, UUID id) {
    access.requireAccess(owner);
    readings.delete(requireInProgress(owner, id));
  }

  @Transactional(readOnly = true)
  public List<ReadingCommentResponse> comments(UUID owner, UUID readingId) {
    access.requireAccess(owner);
    reading(owner, readingId);
    return commentMapper.toResponses(
        comments.findAllByReadingIdAndOwnerIdOrderByCreatedAtAscIdAsc(readingId, owner));
  }

  @Transactional
  public ReadingCommentResponse addComment(
      UUID owner,
      UUID readingId,
      SaveReadingCommentRequest request) {
    access.requireAccess(owner);
    reading(owner, readingId);
    OffsetDateTime createdAt = now();
    ReadingComment comment = comments.save(ReadingComment.builder()
        .id(UUID.randomUUID())
        .ownerId(owner)
        .readingId(readingId)
        .body(request.body().trim())
        .createdAt(createdAt)
        .updatedAt(createdAt)
        .build());
    return commentMapper.toResponse(comment);
  }

  @Transactional
  public ReadingCommentResponse updateComment(
      UUID owner,
      UUID readingId,
      UUID commentId,
      SaveReadingCommentRequest request) {
    access.requireAccess(owner);
    reading(owner, readingId);
    ReadingComment comment = comments.findByIdAndReadingIdAndOwnerId(commentId, readingId, owner)
        .orElseThrow(() -> notFound("Comment not found."));
    comment.update(request.body().trim(), now());
    return commentMapper.toResponse(comment);
  }

  @Transactional
  public void deleteComment(UUID owner, UUID readingId, UUID commentId) {
    access.requireAccess(owner);
    reading(owner, readingId);
    ReadingComment comment = comments.findByIdAndReadingIdAndOwnerId(commentId, readingId, owner)
        .orElseThrow(() -> notFound("Comment not found."));
    comments.delete(comment);
  }

  private void updateSpread(Reading reading, String spreadId) {
    if (spreadId == null) {
      throw badRequest("spreadId cannot be null.");
    }
    requireSpread(spreadId);
    positions.deleteAllByReadingId(reading.getId());
    positions.flush();
    copySpreadPositions(reading.getId(), spreadId, now());
  }

  private void copySpreadPositions(UUID readingId, String spreadId, OffsetDateTime createdAt) {
    List<ReadingPosition> copies = spreadPositions
        .findAllBySpread_IdOrderByPositionOrderAsc(spreadId)
        .stream()
        .map(position -> ReadingPosition.builder()
            .id(UUID.randomUUID())
            .readingId(readingId)
            .spreadPositionId(position.getId())
            .positionKey(position.getPositionKey())
            .positionOrder(position.getPositionOrder())
            .namePtBr(position.getNamePtBr())
            .nameEn(position.getNameEn())
            .meaningPtBr(position.getMeaningPtBr())
            .meaningEn(position.getMeaningEn())
            .x(position.getX())
            .y(position.getY())
            .rotation(position.getRotation())
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build())
        .toList();
    positions.saveAll(copies);
  }

  private void validateCardForDeck(Reading reading, String cardId) {
    if (cardId == null) {
      return;
    }
    TarotCard card = cards.findById(cardId)
        .orElseThrow(() -> notFound("Card not found."));
    if (reading.getDeckMode() == ReadingDeckMode.MAJOR && !"major".equals(card.getSuit())) {
      throw badRequest("Card is not allowed in this deck.");
    }
  }

  private Reading reading(UUID owner, UUID id) {
    return readings.findByIdAndOwnerId(id, owner)
        .orElseThrow(() -> notFound("Reading not found."));
  }

  private Reading requireInProgress(UUID owner, UUID id) {
    Reading reading = reading(owner, id);
    if (reading.getStatus() != ReadingStatus.IN_PROGRESS) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed readings are immutable.");
    }
    return reading;
  }

  private void requireClient(UUID owner, UUID id) {
    if (id != null && clients.findByIdAndOwnerId(id, owner).isEmpty()) {
      throw notFound("Client not found.");
    }
  }

  private Spread requireSpread(String id) {
    return spreads.findByIdAndActiveTrue(id)
        .orElseThrow(() -> notFound("Spread not found."));
  }

  private ReadingDeckMode deckMode(String value) {
    try {
      return ReadingDeckMode.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw badRequest("Invalid deck mode.");
    }
  }

  private ReadingStatus readingStatus(String value) {
    try {
      return ReadingStatus.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw badRequest("Invalid reading status.");
    }
  }

  private ReadingResponse response(Reading reading, String locale) {
    UUID readingShareId = shares.findFirstByReading_IdAndStatusAndExpiresAtAfter(
            reading.getId(),
            ReadingShareStatus.ACTIVE,
            now())
        .map(ReadingShare::getId)
        .orElse(null);
    Spread spread = spreads.findById(reading.getSpreadId()).orElseThrow();
    List<ReadingPosition> readingPositions =
        positions.findAllByReadingIdOrderByPositionOrderAsc(reading.getId());
    List<TarotCard> readingCards = cards.findAllById(readingPositions.stream()
        .map(ReadingPosition::getCardId)
        .filter(java.util.Objects::nonNull)
        .toList());
    Map<String, TarotCard> cardsById = readingCards.stream()
        .collect(Collectors.toMap(TarotCard::getId, Function.identity()));
    List<ReadingPositionResponse> positionResponses = readingPositions.stream()
        .map(position -> positionMapper.toResponse(
            position,
            cardsById.get(position.getCardId()),
            locale))
        .toList();
    return mapper.toResponse(
        reading,
        readingShareId,
        spreadMapper.toSummaryResponse(spread, locale),
        positionResponses);
  }

  private Map<UUID, UUID> activeShareIds(
      List<Reading> page,
      OffsetDateTime currentTime) {
    if (page.isEmpty()) {
      return Map.of();
    }
    return shares.findAllByReading_IdInAndStatusAndExpiresAtAfter(
            page.stream().map(Reading::getId).toList(),
            ReadingShareStatus.ACTIVE,
            currentTime)
        .stream()
        .collect(Collectors.toMap(share -> share.getReading().getId(), ReadingShare::getId));
  }

}
