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
@Table(name = "readings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reading {
  @Id
  private UUID id;
  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;
  @Column(name = "client_id")
  private UUID clientId;
  @Column(name = "spread_id", nullable = false, length = 80)
  private String spreadId;
  @Column(name = "deck_mode", nullable = false, length = 8)
  private String deckMode;
  @Column(nullable = false, length = 16)
  private String status;
  @Column(length = 200)
  private String title;
  @Column(length = 5000)
  private String question;
  @Column(length = 10000)
  private String context;
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

  public Reading(
      UUID ownerId,
      UUID clientId,
      String spreadId,
      String deckMode,
      String title,
      String question,
      String context,
      OffsetDateTime createdAt) {
    id = UUID.randomUUID();
    this.ownerId = ownerId;
    this.clientId = clientId;
    this.spreadId = spreadId;
    this.deckMode = deckMode;
    status = "IN_PROGRESS";
    this.title = title;
    this.question = question;
    this.context = context;
    startedAt = createdAt;
    this.createdAt = createdAt;
    updatedAt = createdAt;
  }

  public void update(
      boolean clientPresent,
      UUID clientId,
      boolean spreadPresent,
      String spreadId,
      boolean deckPresent,
      String deckMode,
      boolean titlePresent,
      String title,
      boolean questionPresent,
      String question,
      boolean contextPresent,
      String context,
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
    this.updatedAt = updatedAt;
  }

  public void complete(OffsetDateTime completedAt) {
    status = "COMPLETED";
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

  public UUID getClientId() {
    return clientId;
  }

  public String getDeckMode() {
    return deckMode;
  }

  public String getTitle() {
    return title;
  }

  public String getContext() {
    return context;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
