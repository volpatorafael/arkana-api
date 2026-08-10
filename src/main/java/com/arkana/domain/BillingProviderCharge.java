package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "billing_provider_charges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingProviderCharge {
  @Id
  private UUID id;
  @Column(name = "provider_subscription_id", nullable = false)
  private UUID providerSubscriptionId;
  @Column(nullable = false, length = 32)
  @Enumerated(EnumType.STRING)
  private BillingProvider provider;
  @Column(name = "provider_charge_id", nullable = false, length = 200)
  private String providerChargeId;
  @Column(name = "due_at", nullable = false)
  private OffsetDateTime dueAt;
  @Column(nullable = false)
  private int amount;
  @Column(nullable = false, length = 32)
  @Enumerated(EnumType.STRING)
  private BillingProviderChargeStatus status;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @PrePersist
  void createTimestamp() {
    createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
}

