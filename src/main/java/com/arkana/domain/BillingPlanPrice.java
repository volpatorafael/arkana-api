package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "billing_plan_prices")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingPlanPrice {
  @Id
  private UUID id;
  @Column(nullable = false, unique = true, length = 64)
  private String code;
  @Column(nullable = false, length = 120)
  private String name;
  @Column(name = "billing_interval", nullable = false, length = 8)
  private String billingInterval;
  @Column(nullable = false)
  private int amount;
  @Column(name = "compare_at_amount")
  private Integer compareAtAmount;
  @Column(nullable = false, length = 3)
  private String currency;
  @Column(name = "trial_days", nullable = false)
  private int trialDays;
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "available_payment_methods", nullable = false)
  private List<String> availablePaymentMethods;
  @Column(nullable = false)
  private boolean active;
  @Column(name = "is_default", nullable = false)
  private boolean defaultPlan;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  public BillingPlanPrice(
      UUID id,
      String code,
      String name,
      String billingInterval,
      int amount,
      Integer compareAtAmount,
      String currency,
      int trialDays,
      List<String> availablePaymentMethods,
      boolean active,
      boolean defaultPlan) {
    this.id = id;
    this.code = code;
    this.name = name;
    this.billingInterval = billingInterval;
    this.amount = amount;
    this.compareAtAmount = compareAtAmount;
    this.currency = currency;
    this.trialDays = trialDays;
    this.availablePaymentMethods = availablePaymentMethods;
    this.active = active;
    this.defaultPlan = defaultPlan;
  }

  @PrePersist
  void createTimestamp() {
    createdAt = OffsetDateTime.now(ZoneOffset.UTC);
  }
}
