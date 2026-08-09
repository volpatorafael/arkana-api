package com.arkana.repository;

import com.arkana.domain.ReadingShare;
import com.arkana.domain.ReadingShareStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingShareRepository extends JpaRepository<ReadingShare, UUID> {
  long countByReading_IdAndStatus(UUID readingId, ReadingShareStatus status);

  Optional<ReadingShare> findFirstByReading_IdAndStatus(
      UUID readingId,
      ReadingShareStatus status);

  List<ReadingShare> findAllByReading_IdInAndStatusAndExpiresAtAfter(
      Collection<UUID> readingIds,
      ReadingShareStatus status,
      OffsetDateTime currentTime);

  Optional<ReadingShare> findFirstByReading_IdAndStatusAndExpiresAtAfter(
      UUID readingId,
      ReadingShareStatus status,
      OffsetDateTime currentTime);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update ReadingShare share
      set share.accessCount = share.accessCount + 1
      where share.id = :id
        and share.status = :status
        and share.expiresAt > :currentTime
      """)
  int incrementAccessCount(
      @Param("id") UUID id,
      @Param("status") ReadingShareStatus status,
      @Param("currentTime") OffsetDateTime currentTime);
}
