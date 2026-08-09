package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "spread_positions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpreadPosition {
  @Id
  private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "spread_id", nullable = false)
  private Spread spread;
  @Column(name = "position_key", nullable = false)
  private String positionKey;
  @Column(name = "position_order", nullable = false)
  private short positionOrder;
  @Column(name = "name_pt_br", nullable = false)
  private String namePtBr;
  @Column(name = "name_en", nullable = false)
  private String nameEn;
  @Column(name = "meaning_pt_br", nullable = false)
  private String meaningPtBr;
  @Column(name = "meaning_en", nullable = false)
  private String meaningEn;
  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal x;
  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal y;
  @Column(nullable = false)
  private short rotation;

}
