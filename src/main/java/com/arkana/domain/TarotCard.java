package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tarot_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarotCard {
  @Id
  private String id;
  @Column(name = "card_number", nullable = false)
  private short cardNumber;
  @Column(nullable = false, length = 16)
  private String suit;
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
