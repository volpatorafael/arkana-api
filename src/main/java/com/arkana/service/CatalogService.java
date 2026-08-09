package com.arkana.service;

import com.arkana.domain.ReadingDeckMode;
import com.arkana.domain.Spread;
import com.arkana.domain.TarotCard;
import com.arkana.dto.catalog.SpreadPositionResponse;
import com.arkana.dto.catalog.SpreadResponse;
import com.arkana.dto.catalog.TarotCardResponse;
import com.arkana.repository.SpreadRepository;
import com.arkana.repository.TarotCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogService {
  private final ProductAccessAuthorizer access;
  private final TarotCardRepository cards;
  private final SpreadRepository spreads;

  @Transactional(readOnly = true)
  public List<TarotCardResponse> cards(UUID userId, String deckMode, String locale) {
    access.requireAccess(userId);
    String normalizedLocale = locale(locale);
    ReadingDeckMode normalizedMode;
    try {
      normalizedMode = deckMode == null
          ? ReadingDeckMode.FULL
          : ReadingDeckMode.valueOf(deckMode);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deckMode must be FULL or MAJOR.");
    }
    List<TarotCard> rows = normalizedMode == ReadingDeckMode.MAJOR
        ? cards.findAllBySuitOrderByCardNumberAsc("major")
        : cards.findAllByOrderBySuitAscCardNumberAsc();
    return rows.stream().map(card -> card(card, normalizedLocale)).toList();
  }

  @Transactional(readOnly = true)
  public List<SpreadResponse> spreads(UUID userId, String locale) {
    access.requireAccess(userId);
    String normalizedLocale = locale(locale);
    return spreads.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
        .map(spread -> spread(spread, normalizedLocale)).toList();
  }

  @Transactional(readOnly = true)
  public SpreadResponse spread(UUID userId, String spreadId, String locale) {
    access.requireAccess(userId);
    Spread row = spreads.findByIdAndActiveTrue(spreadId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Spread not found."));
    return spread(row, locale(locale));
  }

  private String locale(String locale) {
    String value = locale == null ? "pt-BR" : locale;
    if (!value.equals("pt-BR") && !value.equals("en")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "locale must be pt-BR or en.");
    }
    return value;
  }

  private TarotCardResponse card(TarotCard card, String locale) {
    boolean ptBr = locale.equals("pt-BR");
    return new TarotCardResponse(card.getId(), card.getCardNumber(), card.getSuit(),
        ptBr ? card.getNamePtBr() : card.getNameEn(),
        ptBr ? card.getDescriptionPtBr() : card.getDescriptionEn(),
        ptBr ? card.getLightPtBr() : card.getLightEn(),
        ptBr ? card.getShadowPtBr() : card.getShadowEn());
  }

  private SpreadResponse spread(Spread spread, String locale) {
    boolean ptBr = locale.equals("pt-BR");
    List<SpreadPositionResponse> positions = spread.getPositions().stream()
        .map(position -> new SpreadPositionResponse(
            position.getId(),
            position.getPositionKey(),
            position.getPositionOrder(),
            ptBr ? position.getNamePtBr() : position.getNameEn(),
            ptBr ? position.getMeaningPtBr() : position.getMeaningEn(),
            position.getX(),
            position.getY(),
            position.getRotation()))
        .toList();
    return new SpreadResponse(spread.getId(), ptBr ? spread.getNamePtBr() : spread.getNameEn(),
        ptBr ? spread.getShortDescriptionPtBr() : spread.getShortDescriptionEn(),
        ptBr ? spread.getDescriptionPtBr() : spread.getDescriptionEn(),
        ptBr ? spread.getUseCasePtBr() : spread.getUseCaseEn(),
        spread.getPositionCount(), spread.isActive(), positions);
  }
}
