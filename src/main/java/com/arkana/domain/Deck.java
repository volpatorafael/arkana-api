package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "decks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Deck {
  // TODO(multi-deck): transitional default for the compatibility window
  // where deckId is optional (frontend doesn't send it yet — see
  // ReadingService.create, CreateReadingRequest, CatalogService.cards).
  // Once every caller sends deckId explicitly, delete this constant and the
  // null-coalescing that uses it.
  public static final String DEFAULT_DECK_ID = "rider-waite";

  @Id
  private String id;
  @Column(name = "display_order", nullable = false)
  private short displayOrder;
  @Column(name = "name_pt_br", nullable = false)
  private String namePtBr;
  @Column(name = "name_en", nullable = false)
  private String nameEn;
  @Column(name = "card_count", nullable = false)
  private short cardCount;
  @Column(nullable = false)
  private boolean active;
}
