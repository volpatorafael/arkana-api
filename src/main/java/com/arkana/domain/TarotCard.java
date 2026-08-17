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
@Table(name = "tarot_cards")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TarotCard {
  // Cross-deck sentinel: every deck's major-arcana track uses this suit
  // value (not a closed enum — see the openapi.yaml note on TarotCard.suit).
  public static final String MAJOR_SUIT = "major";

  @Id
  private String id;
  @Column(name = "deck_id", nullable = false)
  private String deckId;
  @Column(name = "card_number", nullable = false)
  private short cardNumber;
  @Column(nullable = false, length = 16)
  private String suit;
  @Column(name = "image_path", nullable = false, length = 80)
  private String imagePath;
  @Column(name = "name_pt_br", nullable = false)
  private String namePtBr;
  @Column(name = "name_en", nullable = false)
  private String nameEn;
  @Column(name = "description_pt_br", nullable = false)
  private String descriptionPtBr;
  @Column(name = "description_en", nullable = false)
  private String descriptionEn;
  @Column(name = "light_pt_br", nullable = false)
  private String lightPtBr;
  @Column(name = "light_en", nullable = false)
  private String lightEn;
  @Column(name = "shadow_pt_br", nullable = false)
  private String shadowPtBr;
  @Column(name = "shadow_en", nullable = false)
  private String shadowEn;
}
