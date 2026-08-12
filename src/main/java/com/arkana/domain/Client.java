package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {
  @Id
  private UUID id;
  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;
  @Column(nullable = false, length = 160)
  private String name;
  @Column(name = "birth_date")
  private LocalDate birthDate;
  @Column(length = 320)
  private String email;
  @Column(length = 40)
  private String phone;
  @Column(length = 10000)
  private String notes;
  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  void createTimestamps() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void updateTimestamp() {
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public void update(
      String name,
      LocalDate birthDate,
      String email,
      String phone,
      String notes) {
    this.name = name;
    this.birthDate = birthDate;
    this.email = email;
    this.phone = phone;
    this.notes = notes;
  }

  public void archive() {
    archivedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public void restore() {
    archivedAt = null;
  }

}
