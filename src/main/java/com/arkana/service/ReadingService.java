package com.arkana.service;

import com.arkana.domain.Reading;
import com.arkana.domain.ReadingComment;
import com.arkana.domain.ReadingPosition;
import com.arkana.domain.Spread;
import com.arkana.domain.TarotCard;
import com.arkana.dto.reading.CreateReadingRequest;
import com.arkana.dto.reading.SaveReadingCommentRequest;
import com.arkana.dto.reading.SaveReadingPositionRequest;
import com.arkana.dto.reading.UpdateReadingRequest;
import com.arkana.repository.ClientRepository;
import com.arkana.repository.ReadingCommentRepository;
import com.arkana.repository.ReadingPositionRepository;
import com.arkana.repository.ReadingRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReadingService {
  private final ReadingRepository readings;
  private final ReadingPositionRepository positions;
  private final ReadingCommentRepository comments;
  private final SpreadRepository spreads;
  private final SpreadPositionRepository spreadPositions;
  private final TarotCardRepository cards;
  private final ClientRepository clients;
  private final ProductAccessAuthorizer access;

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

  private static Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < values.length; index += 2) {
      result.put((String) values[index], values[index + 1]);
    }
    return result;
  }

  @Transactional
  public Map<String, Object> create(UUID owner, CreateReadingRequest request, String locale) {
    access.requireAccess(owner);
    validateDeck(request.deckMode());
    requireSpread(request.spreadId());
    requireClient(owner, request.clientId());

    OffsetDateTime createdAt = now();
    Reading reading = readings.save(Reading.builder()
        .id(UUID.randomUUID())
        .ownerId(owner)
        .clientId(request.clientId())
        .spreadId(request.spreadId())
        .deckMode(request.deckMode())
        .status("IN_PROGRESS")
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
  public Map<String, Object> get(UUID owner, UUID id, String locale) {
    access.requireAccess(owner);
    return response(reading(owner, id), locale);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> list(
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
    if (status != null) {
      validateStatus(status);
    }

    Specification<Reading> specification = (root, query, builder) ->
        builder.equal(root.get("ownerId"), owner);
    specification = specification.and((root, query, builder) -> archived
        ? builder.isNotNull(root.get("archivedAt"))
        : builder.isNull(root.get("archivedAt")));
    if (clientId != null) {
      specification = specification.and((root, query, builder) ->
          builder.equal(root.get("clientId"), clientId));
    }
    if (status != null) {
      specification = specification.and((root, query, builder) ->
          builder.equal(root.get("status"), status));
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
    return map(
        "items", result.getContent().stream().map(this::readingFields).toList(),
        "page", page,
        "pageSize", pageSize,
        "total", result.getTotalElements());
  }

  @Transactional
  public Map<String, Object> update(UUID owner, UUID id, UpdateReadingRequest request, String locale) {
    access.requireAccess(owner);
    Reading reading = requireInProgress(owner, id);
    if (!request.any()) {
      throw badRequest("At least one field must be provided.");
    }
    if (request.deckPresent()) {
      if (request.deckMode() == null) {
        throw badRequest("deckMode cannot be null.");
      }
      validateDeck(request.deckMode());
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
        request.deckMode(),
        request.titlePresent(),
        trim(request.title()),
        request.questionPresent(),
        trim(request.question()),
        request.contextPresent(),
        trim(request.context()),
        now());
    return response(reading, locale);
  }

  @Transactional
  public Map<String, Object> savePosition(
      UUID owner,
      UUID readingId,
      UUID positionId,
      SaveReadingPositionRequest request,
      String locale) {
    access.requireAccess(owner);
    Reading reading = requireInProgress(owner, readingId);
    if ((request.cardId() == null) != (request.orientation() == null)) {
      throw badRequest("cardId and orientation must both be set or cleared.");
    }
    if (request.orientation() != null
        && !List.of("UPRIGHT", "REVERSED").contains(request.orientation())) {
      throw badRequest("Invalid orientation.");
    }
    validateCardForDeck(reading, request.cardId());

    ReadingPosition position = positions.findByIdAndReadingId(positionId, readingId)
        .orElseThrow(() -> notFound("Reading position not found."));
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
    return position(position, card, locale);
  }

  @Transactional
  public Map<String, Object> complete(UUID owner, UUID id, String locale) {
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
  public Map<String, Object> archive(UUID owner, UUID id, String locale) {
    access.requireAccess(owner);
    Reading reading = reading(owner, id);
    reading.archive(now());
    return response(reading, locale);
  }

  @Transactional
  public Map<String, Object> restore(UUID owner, UUID id, String locale) {
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
  public List<Map<String, Object>> comments(UUID owner, UUID readingId) {
    access.requireAccess(owner);
    reading(owner, readingId);
    return comments.findAllByReadingIdAndOwnerIdOrderByCreatedAtAscIdAsc(readingId, owner).stream()
        .map(this::comment)
        .toList();
  }

  @Transactional
  public Map<String, Object> addComment(UUID owner, UUID readingId, SaveReadingCommentRequest request) {
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
    return comment(comment);
  }

  @Transactional
  public Map<String, Object> updateComment(
      UUID owner,
      UUID readingId,
      UUID commentId,
      SaveReadingCommentRequest request) {
    access.requireAccess(owner);
    reading(owner, readingId);
    ReadingComment comment = comments.findByIdAndReadingIdAndOwnerId(commentId, readingId, owner)
        .orElseThrow(() -> notFound("Comment not found."));
    comment.update(request.body().trim(), now());
    return comment(comment);
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
    if ("MAJOR".equals(reading.getDeckMode()) && !"major".equals(card.getSuit())) {
      throw badRequest("Card is not allowed in this deck.");
    }
  }

  private Reading reading(UUID owner, UUID id) {
    return readings.findByIdAndOwnerId(id, owner)
        .orElseThrow(() -> notFound("Reading not found."));
  }

  private Reading requireInProgress(UUID owner, UUID id) {
    Reading reading = reading(owner, id);
    if (!"IN_PROGRESS".equals(reading.getStatus())) {
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

  private void validateDeck(String value) {
    if (!List.of("FULL", "MAJOR").contains(value)) {
      throw badRequest("Invalid deck mode.");
    }
  }

  private void validateStatus(String value) {
    if (!List.of("IN_PROGRESS", "COMPLETED").contains(value)) {
      throw badRequest("Invalid reading status.");
    }
  }

  private Map<String, Object> response(Reading reading, String locale) {
    Map<String, Object> result = readingFields(reading);
    Spread spread = spreads.findById(reading.getSpreadId()).orElseThrow();
    result.put("spread", map(
        "id", spread.getId(),
        "name", "en".equals(locale) ? spread.getNameEn() : spread.getNamePtBr()));
    result.put("positions", positionResponses(reading.getId(), locale));
    return result;
  }

  private Map<String, Object> readingFields(Reading reading) {
    return map(
        "id", reading.getId(),
        "clientId", reading.getClientId(),
        "spreadId", reading.getSpreadId(),
        "deckMode", reading.getDeckMode(),
        "status", reading.getStatus(),
        "title", reading.getTitle(),
        "question", reading.getQuestion(),
        "context", reading.getContext(),
        "startedAt", reading.getStartedAt(),
        "completedAt", reading.getCompletedAt(),
        "archivedAt", reading.getArchivedAt(),
        "createdAt", reading.getCreatedAt(),
        "updatedAt", reading.getUpdatedAt());
  }

  private List<Map<String, Object>> positionResponses(UUID readingId, String locale) {
    List<ReadingPosition> readingPositions = positions.findAllByReadingIdOrderByPositionOrderAsc(readingId);
    Map<String, TarotCard> cardsById = cards.findAllById(readingPositions.stream()
            .map(ReadingPosition::getCardId)
            .filter(java.util.Objects::nonNull)
            .toList())
        .stream()
        .collect(Collectors.toMap(TarotCard::getId, Function.identity()));
    return readingPositions.stream()
        .map(position -> position(position, cardsById.get(position.getCardId()), locale))
        .toList();
  }

  private Map<String, Object> position(
      ReadingPosition position,
      TarotCard card,
      String locale) {
    Object cardValue = card == null
        ? null
        : map(
        "id", card.getId(),
        "number", card.getCardNumber(),
        "suit", card.getSuit(),
        "name", "en".equals(locale) ? card.getNameEn() : card.getNamePtBr());
    return map(
        "id", position.getId(),
        "key", position.getPositionKey(),
        "order", position.getPositionOrder(),
        "name", "en".equals(locale) ? position.getNameEn() : position.getNamePtBr(),
        "meaning", "en".equals(locale) ? position.getMeaningEn() : position.getMeaningPtBr(),
        "x", position.getX(),
        "y", position.getY(),
        "rotation", position.getRotation(),
        "card", cardValue,
        "orientation", position.getOrientation(),
        "interpretation", position.getInterpretation(),
        "createdAt", position.getCreatedAt(),
        "updatedAt", position.getUpdatedAt());
  }

  private Map<String, Object> comment(ReadingComment comment) {
    return map(
        "id", comment.getId(),
        "readingId", comment.getReadingId(),
        "body", comment.getBody(),
        "createdAt", comment.getCreatedAt(),
        "updatedAt", comment.getUpdatedAt());
  }
}
