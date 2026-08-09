package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "billing_access_overrides")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingAccessOverride {
  @Id
  private UUID id;
  @Column(name = "billing_account_id", nullable = false)
  private UUID billingAccountId;
  @Column(name = "starts_at", nullable = false)
  private OffsetDateTime startsAt;
  @Column(name = "ends_at", nullable = false)
  private OffsetDateTime endsAt;
  @Column(nullable = false, length = 500)
  private String reason;
  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;
}
