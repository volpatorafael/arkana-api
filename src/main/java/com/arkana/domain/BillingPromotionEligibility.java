package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_promotion_eligibilities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPromotionEligibility {
  @Id
  private UUID id;
  @Column(name = "billing_account_id", nullable = false)
  private UUID billingAccountId;
  @Column(name = "campaign_id", nullable = false)
  private UUID campaignId;
  @Column(nullable = false, length = 16)
  private String status;
  @Column(name = "granted_at", nullable = false)
  private OffsetDateTime grantedAt;
  @Column(name = "first_checkout_ends_at", nullable = false)
  private OffsetDateTime firstCheckoutEndsAt;
  @Column(name = "locked_at")
  private OffsetDateTime lockedAt;
  @Column(name = "expired_at")
  private OffsetDateTime expiredAt;
  @Column(name = "forfeited_at")
  private OffsetDateTime forfeitedAt;

  public BillingPromotionEligibility(
      UUID billingAccountId,
      UUID campaignId,
      OffsetDateTime grantedAt,
      OffsetDateTime firstCheckoutEndsAt) {
    id = UUID.randomUUID();
    this.billingAccountId = billingAccountId;
    this.campaignId = campaignId;
    status = "ELIGIBLE";
    this.grantedAt = grantedAt;
    this.firstCheckoutEndsAt = firstCheckoutEndsAt;
  }

  public void lock(OffsetDateTime lockedAt) {
    status = "LOCKED";
    this.lockedAt = lockedAt;
  }

  public void forfeit(OffsetDateTime forfeitedAt) {
    status = "FORFEITED";
    this.forfeitedAt = forfeitedAt;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getFirstCheckoutEndsAt() {
    return firstCheckoutEndsAt;
  }
}
