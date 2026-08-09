package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile {
  @Id
  private UUID id;
  @Column(nullable = false, length = 320)
  private String email;
  @Column(name = "display_name", length = 120)
  private String displayName;
  @Column(nullable = false, length = 8)
  private String locale;
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public Profile(UUID id, String email, String locale) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    this.id = id;
    this.email = email;
    this.locale = locale;
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void touch() {
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public void updateDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void updateLocale(String locale) {
    this.locale = locale;
  }

  public String getEmail() {
    return email;
  }

  public String getLocale() {
    return locale;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
