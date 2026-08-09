package com.arkana.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
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
@Table(name = "profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

}
