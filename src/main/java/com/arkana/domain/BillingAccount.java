package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "billing_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingAccount {
  @Id
  private UUID id;

  @Column(name = "owner_id", nullable = false, unique = true)
  private UUID ownerId;

  @Column(name = "trial_email_fingerprint", length = 64, unique = true)
  private String trialEmailFingerprint;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "trial_started_at")
  private OffsetDateTime trialStartedAt;

  @Column(name = "trial_ends_at")
  private OffsetDateTime trialEndsAt;
  @Column(name = "current_plan_price_id")
  private UUID currentPlanPriceId;
  @Column(name = "pending_plan_price_id")
  private UUID pendingPlanPriceId;
  @Column(name = "current_period_start")
  private OffsetDateTime currentPeriodStart;
  @Column(name = "current_period_end")
  private OffsetDateTime currentPeriodEnd;
  @Column(name = "cancel_at_period_end", nullable = false)
  private boolean cancelAtPeriodEnd;
  @Column(name = "override_ends_at")
  private OffsetDateTime overrideEndsAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public BillingAccount(UUID ownerId, String fingerprint, OffsetDateTime trialStartedAt, OffsetDateTime trialEndsAt) {
    this.id = UUID.randomUUID();
    this.ownerId = ownerId;
    this.trialEmailFingerprint = fingerprint;
    this.status = "TRIALING";
    this.trialStartedAt = trialStartedAt;
    this.trialEndsAt = trialEndsAt;
  }

  @PrePersist
  void createTimestamps() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void updateTimestamp() {
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public void cancelAtPeriodEnd() {
    status = "CANCEL_AT_PERIOD_END";
    cancelAtPeriodEnd = true;
  }

  public void schedulePlan(UUID planId) {
    pendingPlanPriceId = planId;
  }

  public void applyProviderState(
      String status,
      UUID planId,
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd,
      OffsetDateTime trialEnd) {
    this.status = status;
    if (planId != null) {
      currentPlanPriceId = planId;
    }
    pendingPlanPriceId = null;
    currentPeriodStart = periodStart;
    currentPeriodEnd = periodEnd;
    if (trialEnd != null) {
      trialEndsAt = trialEnd;
    }
  }
}
