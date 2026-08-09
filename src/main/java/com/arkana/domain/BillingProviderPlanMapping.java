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

import java.util.UUID;

@Entity
@Table(name = "billing_provider_plan_mappings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingProviderPlanMapping {
  @Id
  private UUID id;
  @Column(name = "plan_price_id", nullable = false)
  private UUID planPriceId;
  @Column(nullable = false, length = 32)
  @Enumerated(EnumType.STRING)
  private BillingProvider provider;
  @Column(name = "provider_product_id", nullable = false, length = 200)
  private String providerProductId;

}
