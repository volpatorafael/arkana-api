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
@Table(name = "readings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reading {
  @Id
  private UUID id;
  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;
  @Column(name = "client_id")
  private UUID clientId;
  @Column(name = "spread_id", nullable = false, length = 80)
  private String spreadId;
  @Column(name = "deck_id", nullable = false, length = 40)
  private String deckId;
  @Column(name = "deck_mode", nullable = false, length = 8)
  @Enumerated(EnumType.STRING)
  private ReadingDeckMode deckMode;
  @Column(nullable = false, length = 16)
  @Enumerated(EnumType.STRING)
  private ReadingStatus status;
  @Column(length = 200)
  private String title;
  @Column(length = 5000)
  private String question;
  @Column(length = 10000)
  private String context;
  @Column(name = "consultation_fee_amount")
  private Integer consultationFeeAmount;
  @Builder.Default
  @Column(name = "consultation_fee_currency", nullable = false, length = 3)
  @Enumerated(EnumType.STRING)
  private CurrencyCode consultationFeeCurrency = CurrencyCode.BRL;
  @Column(name = "consultation_duration_minutes")
  private Integer consultationDurationMinutes;
  @Column(name = "analysis_video_url", length = 2048)
  private String analysisVideoUrl;
  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;
  @Column(name = "completed_at")
  private OffsetDateTime completedAt;
  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public void update(
      boolean clientPresent,
      UUID clientId,
      boolean spreadPresent,
      String spreadId,
      boolean deckPresent,
      ReadingDeckMode deckMode,
      boolean titlePresent,
      String title,
      boolean questionPresent,
      String question,
      boolean contextPresent,
      String context,
      boolean consultationFeeAmountPresent,
      Integer consultationFeeAmount,
      boolean consultationDurationMinutesPresent,
      Integer consultationDurationMinutes,
      boolean analysisVideoUrlPresent,
      String analysisVideoUrl,
      boolean startedAtPresent,
      OffsetDateTime startedAt,
      OffsetDateTime updatedAt) {
    if (clientPresent) {
      this.clientId = clientId;
    }
    if (spreadPresent) {
      this.spreadId = spreadId;
    }
    if (deckPresent) {
      this.deckMode = deckMode;
    }
    if (titlePresent) {
      this.title = title;
    }
    if (questionPresent) {
      this.question = question;
    }
    if (contextPresent) {
      this.context = context;
    }
    if (consultationFeeAmountPresent) {
      this.consultationFeeAmount = consultationFeeAmount;
    }
    if (consultationDurationMinutesPresent) {
      this.consultationDurationMinutes = consultationDurationMinutes;
    }
    if (analysisVideoUrlPresent) {
      this.analysisVideoUrl = analysisVideoUrl;
    }
    if (startedAtPresent) {
      this.startedAt = startedAt;
    }
    this.updatedAt = updatedAt;
  }

  public void complete(OffsetDateTime completedAt) {
    status = ReadingStatus.COMPLETED;
    this.completedAt = completedAt;
    updatedAt = completedAt;
  }

  public void archive(OffsetDateTime archivedAt) {
    this.archivedAt = archivedAt;
    updatedAt = archivedAt;
  }

  public void restore(OffsetDateTime updatedAt) {
    archivedAt = null;
    this.updatedAt = updatedAt;
  }

}
