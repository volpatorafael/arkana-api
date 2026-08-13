package com.arkana.service;

import com.arkana.domain.ReadingDeckMode;
import com.arkana.domain.TarotCard;
import com.arkana.dto.catalog.SpreadResponse;
import com.arkana.dto.catalog.TarotCardResponse;
import com.arkana.mapper.TarotCardMapper;
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
  private final TarotCardMapper cardMapper;
  private final LocalizedSpreadCatalogService localizedSpreads;

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
    return rows.stream().map(card -> cardMapper.toResponse(card, normalizedLocale)).toList();
  }

  @Transactional(readOnly = true)
  public List<SpreadResponse> spreads(UUID userId, String locale) {
    access.requireAccess(userId);
    String normalizedLocale = locale(locale);
    return localizedSpreads.spreads(normalizedLocale);
  }

  @Transactional(readOnly = true)
  public SpreadResponse spread(UUID userId, String spreadId, String locale) {
    access.requireAccess(userId);
    String normalizedLocale = locale(locale);
    return localizedSpreads.spreads(normalizedLocale).stream()
        .filter(spread -> spread.id().equals(spreadId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Spread not found."));
  }

  private String locale(String locale) {
    String value = locale == null ? "pt-BR" : locale;
    if (!value.equals("pt-BR") && !value.equals("en")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "locale must be pt-BR or en.");
    }
    return value;
  }

}
