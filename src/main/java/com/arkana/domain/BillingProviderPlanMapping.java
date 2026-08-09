package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "billing_provider_plan_mappings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingProviderPlanMapping {
  @Id
  private UUID id;
  @Column(name = "plan_price_id", nullable = false)
  private UUID planPriceId;
  @Column(nullable = false, length = 32)
  private String provider;
  @Column(name = "provider_product_id", nullable = false, length = 200)
  private String providerProductId;

  public String getProviderProductId() {
    return providerProductId;
  }
}
