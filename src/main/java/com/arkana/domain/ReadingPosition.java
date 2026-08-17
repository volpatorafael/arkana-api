package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reading_positions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingPosition {
  @Id
  private UUID id;
  @Column(name = "reading_id", nullable = false)
  private UUID readingId;
  @Column(name = "spread_position_id")
  private UUID spreadPositionId;
  @Column(name = "position_key", nullable = false, length = 80)
  private String positionKey;
  @Column(name = "position_order", nullable = false)
  private short positionOrder;
  @Column(name = "name_pt_br", nullable = false, length = 200)
  private String namePtBr;
  @Column(name = "name_en", nullable = false, length = 200)
  private String nameEn;
  @Column(name = "meaning_pt_br", nullable = false, length = 2000)
  private String meaningPtBr;
  @Column(name = "meaning_en", nullable = false, length = 2000)
  private String meaningEn;
  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal x;
  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal y;
  @Column(nullable = false)
  private short rotation;
  @Column(name = "stack_order", nullable = false)
  private int stackOrder;
  @Column(name = "card_id", length = 80)
  private String cardId;
  @Column(length = 16)
  @Enumerated(EnumType.STRING)
  private CardOrientation orientation;
  @Column(length = 10000)
  private String interpretation;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public void update(
      String cardId,
      CardOrientation orientation,
      String interpretation,
      OffsetDateTime updatedAt) {
    this.cardId = cardId;
    this.orientation = orientation;
    this.interpretation = interpretation;
    this.updatedAt = updatedAt;
  }

  public void updateLayout(
      BigDecimal x,
      BigDecimal y,
      short rotation,
      int stackOrder,
      OffsetDateTime updatedAt) {
    this.x = x;
    this.y = y;
    this.rotation = rotation;
    this.stackOrder = stackOrder;
    this.updatedAt = updatedAt;
  }

}
