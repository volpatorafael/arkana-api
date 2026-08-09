package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "billing_promotion_campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingPromotionCampaign {
  @Id
  private UUID id;
  @Column(nullable = false, unique = true, length = 64)
  private String code;
  @Column(nullable = false, length = 120)
  private String name;
  @Column(nullable = false, length = 16)
  private String status;
  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;
  @Column(name = "ends_at", nullable = false)
  private OffsetDateTime endsAt;
  @Column(name = "retention_policy", nullable = false, length = 64)
  private String retentionPolicy;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  void createTimestamp() {
    createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

}
