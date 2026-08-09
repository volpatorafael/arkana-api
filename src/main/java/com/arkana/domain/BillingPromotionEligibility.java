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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "billing_promotion_eligibilities")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingPromotionEligibility {
  @Id
  private UUID id;
  @Column(name = "billing_account_id", nullable = false)
  private UUID billingAccountId;
  @Column(name = "campaign_id", nullable = false)
  private UUID campaignId;
  @Column(nullable = false, length = 16)
  @Enumerated(EnumType.STRING)
  private BillingPromotionEligibilityStatus status;
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

  public void lock(OffsetDateTime lockedAt) {
    status = BillingPromotionEligibilityStatus.LOCKED;
    this.lockedAt = lockedAt;
  }

  public void forfeit(OffsetDateTime forfeitedAt) {
    status = BillingPromotionEligibilityStatus.FORFEITED;
    this.forfeitedAt = forfeitedAt;
  }

}
