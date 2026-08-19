package com.arkana.repository;

import com.arkana.domain.Reading;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepository extends JpaRepository<Reading, UUID>, JpaSpecificationExecutor<Reading> {
  Optional<Reading> findByIdAndOwnerId(UUID id, UUID ownerId);

  @Query("""
      select new com.arkana.repository.DashboardCountsProjection(
        (select count(client) from Client client
         where client.ownerId = :ownerId and client.archivedAt is null),
        coalesce(sum(case when reading.status = com.arkana.domain.ReadingStatus.IN_PROGRESS
          then 1L else 0L end), 0L),
        coalesce(sum(case when reading.status = com.arkana.domain.ReadingStatus.COMPLETED
          then 1L else 0L end), 0L))
      from Reading reading
      where reading.ownerId = :ownerId and reading.archivedAt is null
      """)
  DashboardCountsProjection dashboardCounts(@Param("ownerId") UUID ownerId);

  @Query("""
      select new com.arkana.repository.DashboardRecentReadingProjection(
        reading.id,
        reading.title,
        reading.question,
        case when :locale = 'en' then spread.nameEn else spread.namePtBr end,
        reading.status,
        reading.startedAt)
      from Reading reading
      join Spread spread on spread.id = reading.spreadId
      where reading.ownerId = :ownerId and reading.archivedAt is null
      order by reading.startedAt desc, reading.id desc
      """)
  List<DashboardRecentReadingProjection> findDashboardRecentReadings(
      @Param("ownerId") UUID ownerId,
      @Param("locale") String locale,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select reading from Reading reading where reading.id = :id and reading.ownerId = :ownerId")
  Optional<Reading> findByIdAndOwnerIdForUpdate(
      @Param("id") UUID id,
      @Param("ownerId") UUID ownerId);

  @Query("""
      select new com.arkana.repository.AdminUserEventProjection(reading.ownerId, reading.updatedAt)
      from Reading reading
      where reading.updatedAt >= :from and reading.updatedAt < :to
      """)
  List<AdminUserEventProjection> findAdminActivityEvents(
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  @Query("""
      select new com.arkana.repository.AdminUserEventProjection(reading.ownerId, reading.completedAt)
      from Reading reading
      where reading.status = com.arkana.domain.ReadingStatus.COMPLETED
        and reading.completedAt >= :from and reading.completedAt < :to
      """)
  List<AdminUserEventProjection> findAdminCompletedEvents(
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  @Query("""
      select reading.ownerId
      from Reading reading
      where reading.ownerId in :ownerIds
        and reading.status = com.arkana.domain.ReadingStatus.COMPLETED
      """)
  List<UUID> findCompletedOwnerIds(@Param("ownerIds") Collection<UUID> ownerIds);
}
