package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "billing_promotion_campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

  public BillingPromotionCampaign(
      UUID id,
      String code,
      String name,
      String status,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.status = status;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.retentionPolicy = "WHILE_SUBSCRIPTION_ACTIVE";
  }

  @PrePersist
  void createTimestamp() {
    createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public String getCode() {
    return code;
  }

  public OffsetDateTime getEndsAt() {
    return endsAt;
  }
}
