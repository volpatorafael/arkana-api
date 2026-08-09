package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reading_positions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingPosition {
  @Id
  private UUID id;
  @Column(name = "reading_id", nullable = false)
  private UUID readingId;
  @Column(name = "spread_position_id", nullable = false)
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
  @Column(name = "card_id", length = 80)
  private String cardId;
  @Column(length = 16)
  private String orientation;
  @Column(length = 10000)
  private String interpretation;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public ReadingPosition(UUID readingId, SpreadPosition position, OffsetDateTime createdAt) {
    id = UUID.randomUUID();
    this.readingId = readingId;
    spreadPositionId = position.getId();
    positionKey = position.getPositionKey();
    positionOrder = position.getPositionOrder();
    namePtBr = position.getNamePtBr();
    nameEn = position.getNameEn();
    meaningPtBr = position.getMeaningPtBr();
    meaningEn = position.getMeaningEn();
    x = position.getX();
    y = position.getY();
    rotation = position.getRotation();
    this.createdAt = createdAt;
    updatedAt = createdAt;
  }

  public void update(String cardId, String orientation, String interpretation, OffsetDateTime updatedAt) {
    this.cardId = cardId;
    this.orientation = orientation;
    this.interpretation = interpretation;
    this.updatedAt = updatedAt;
  }

  public String getPositionKey() {
    return positionKey;
  }

  public String getNamePtBr() {
    return namePtBr;
  }

  public String getMeaningPtBr() {
    return meaningPtBr;
  }

  public BigDecimal getX() {
    return x;
  }

  public short getRotation() {
    return rotation;
  }

  public String getOrientation() {
    return orientation;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
