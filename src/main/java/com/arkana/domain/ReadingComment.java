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
@Table(name = "reading_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadingComment {
  @Id
  private UUID id;
  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;
  @Column(name = "reading_id", nullable = false)
  private UUID readingId;
  @Column(nullable = false, length = 10000)
  private String body;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public ReadingComment(UUID ownerId, UUID readingId, String body, OffsetDateTime createdAt) {
    id = UUID.randomUUID();
    this.ownerId = ownerId;
    this.readingId = readingId;
    this.body = body;
    this.createdAt = createdAt;
    updatedAt = createdAt;
  }

  public void update(String body, OffsetDateTime updatedAt) {
    this.body = body;
    this.updatedAt = updatedAt;
  }

  public UUID getReadingId() {
    return readingId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
