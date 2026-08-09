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
@Table(name = "reading_comments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

  public void update(String body, OffsetDateTime updatedAt) {
    this.body = body;
    this.updatedAt = updatedAt;
  }

}
