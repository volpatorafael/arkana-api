package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "spreads")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Spread {
  @OneToMany(mappedBy = "spread")
  @OrderBy("positionOrder ASC")
  private final List<SpreadPosition> positions = new ArrayList<>();
  @Id
  private String id;
  @Column(name = "display_order", nullable = false)
  private short displayOrder;
  @Column(name = "name_pt_br", nullable = false)
  private String namePtBr;
  @Column(name = "name_en", nullable = false)
  private String nameEn;
  @Column(name = "short_description_pt_br", nullable = false)
  private String shortDescriptionPtBr;
  @Column(name = "short_description_en", nullable = false)
  private String shortDescriptionEn;
  @Column(name = "description_pt_br", nullable = false)
  private String descriptionPtBr;
  @Column(name = "description_en", nullable = false)
  private String descriptionEn;
  @Column(name = "use_case_pt_br", nullable = false)
  private String useCasePtBr;
  @Column(name = "use_case_en", nullable = false)
  private String useCaseEn;
  @Column(name = "position_count", nullable = false)
  private short positionCount;
  @Column(nullable = false)
  private boolean active;

}
