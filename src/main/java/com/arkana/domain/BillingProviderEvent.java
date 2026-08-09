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
@Table(name = "billing_provider_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingProviderEvent {
  @Id
  private UUID id;
  @Column(nullable = false, length = 32)
  @Enumerated(EnumType.STRING)
  private BillingProvider provider;
  @Column(name = "provider_event_id", nullable = false, length = 200)
  private String providerEventId;
  @Column(name = "event_type", nullable = false, length = 160)
  private String eventType;
  @Column(name = "processing_status", nullable = false, length = 16)
  private String processingStatus;
  @Column(name = "raw_payload", nullable = false, length = 100000)
  private String rawPayload;
  @Column(name = "received_at", nullable = false, updatable = false)
  private OffsetDateTime receivedAt;

  @PrePersist
  void createTimestamp() {
    receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public void ignore() {
    processingStatus = "IGNORED";
  }

  public void process() {
    processingStatus = "PROCESSED";
  }
}
