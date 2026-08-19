package com.arkana.service;

import com.arkana.domain.Deck;
import com.arkana.domain.TarotCard;
import com.arkana.dto.admin.AdminDeckResponse;
import com.arkana.dto.admin.AdminTarotCardResponse;
import com.arkana.dto.admin.CreateAdminDeckRequest;
import com.arkana.dto.admin.CreateAdminTarotCardRequest;
import com.arkana.dto.admin.UpdateAdminDeckRequest;
import com.arkana.dto.admin.UpdateAdminTarotCardRequest;
import com.arkana.mapper.AdminCatalogMapper;
import com.arkana.repository.DeckRepository;
import com.arkana.repository.TarotCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.arkana.config.CacheConfig.LOCALIZED_DECKS;

@Service
@RequiredArgsConstructor
public class AdminCatalogService {
  private final DeckRepository decks;
  private final TarotCardRepository cards;
  private final AdminCatalogMapper adminCatalogMapper;
  @Qualifier("deckCacheManager")
  private final CacheManager deckCacheManager;

  @Transactional(readOnly = true)
  public List<AdminDeckResponse> listDecks() {
    return decks.findAll().stream()
        .map(adminCatalogMapper::toAdminDeck)
        .toList();
  }

  @Transactional(readOnly = true)
  public AdminDeckResponse getDeck(String deckId) {
    Deck deck = decks.findById(deckId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));
    return adminCatalogMapper.toAdminDeck(deck);
  }

  @Transactional
  public AdminDeckResponse createDeck(CreateAdminDeckRequest req) {
    if (decks.existsById(req.id())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Deck id already exists");
    }
    Deck deck = Deck.builder()
        .id(req.id())
        .displayOrder(req.displayOrder() != null ? req.displayOrder().shortValue() : (short) 99)
        .namePtBr(req.namePtBr())
        .nameEn(req.nameEn())
        .cardCount((short) 0)
        .active(req.active() != null ? req.active() : true)
        .build();
    Deck saved = decks.save(deck);
    evictDeckCache();
    return adminCatalogMapper.toAdminDeck(saved);
  }

  @Transactional
  public AdminDeckResponse updateDeck(String deckId, UpdateAdminDeckRequest req) {
    Deck deck = decks.findById(deckId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found"));

    if (req.namePtBr() != null) deck.setNamePtBr(req.namePtBr());
    if (req.nameEn() != null) deck.setNameEn(req.nameEn());
    if (req.displayOrder() != null) deck.setDisplayOrder(req.displayOrder().shortValue());
    if (req.active() != null) deck.setActive(req.active());

    Deck saved = decks.save(deck);
    evictDeckCache();
    return adminCatalogMapper.toAdminDeck(saved);
  }

  @Transactional(readOnly = true)
  public List<AdminTarotCardResponse> listCards(String deckId) {
    if (!decks.existsById(deckId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found");
    }
    return cards.findAllByDeckIdOrderBySuitAscCardNumberAsc(deckId).stream()
        .map(adminCatalogMapper::toAdminCard)
        .toList();
  }

  @Transactional
  public AdminTarotCardResponse createCard(String deckId, CreateAdminTarotCardRequest req) {
    if (!decks.existsById(deckId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deck not found");
    }
    if (cards.existsById(req.id())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Card id already exists");
    }
    TarotCard card = TarotCard.builder()
        .id(req.id())
        .deckId(deckId)
        .cardNumber((short) req.number())
        .suit(req.suit())
        .imagePath(req.imagePath())
        .namePtBr(req.namePtBr())
        .nameEn(req.nameEn())
        .descriptionPtBr(req.descriptionPtBr())
        .descriptionEn(req.descriptionEn())
        .lightPtBr(req.lightPtBr())
        .lightEn(req.lightEn())
        .shadowPtBr(req.shadowPtBr())
        .shadowEn(req.shadowEn())
        .build();
    TarotCard saved = cards.save(card);
    // also increment deck cardCount? for admin create we can keep consistent
    Deck deck = decks.findById(deckId).orElseThrow();
    deck.setCardCount((short) (deck.getCardCount() + 1));
    decks.save(deck);
    evictDeckCache();
    return adminCatalogMapper.toAdminCard(saved);
  }

  @Transactional
  public AdminTarotCardResponse updateCard(String deckId, String cardId, UpdateAdminTarotCardRequest req) {
    TarotCard card = cards.findById(cardId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
    if (!deckId.equals(card.getDeckId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
    }
    card.setNamePtBr(req.namePtBr());
    card.setNameEn(req.nameEn());
    card.setDescriptionPtBr(req.descriptionPtBr());
    card.setDescriptionEn(req.descriptionEn());
    card.setLightPtBr(req.lightPtBr());
    card.setLightEn(req.lightEn());
    card.setShadowPtBr(req.shadowPtBr());
    card.setShadowEn(req.shadowEn());
    TarotCard saved = cards.save(card);
    return adminCatalogMapper.toAdminCard(saved);
  }

  @Transactional
  public void deleteCard(String deckId, String cardId) {
    TarotCard card = cards.findById(cardId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found"));
    if (!deckId.equals(card.getDeckId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found");
    }
    cards.delete(card);
    Deck deck = decks.findById(deckId).orElseThrow();
    short newCount = (short) Math.max(0, deck.getCardCount() - 1);
    deck.setCardCount(newCount);
    decks.save(deck);
  }

  private void evictDeckCache() {
    var cache = deckCacheManager.getCache(LOCALIZED_DECKS);
    if (cache != null) {
      cache.clear();
    }
  }
}
