package com.arkana.repository;

import com.arkana.domain.Reading;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReadingRepository extends JpaRepository<Reading, UUID>, JpaSpecificationExecutor<Reading> {
  Optional<Reading> findByIdAndOwnerId(UUID id, UUID ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select reading from Reading reading where reading.id = :id and reading.ownerId = :ownerId")
  Optional<Reading> findByIdAndOwnerIdForUpdate(
      @Param("id") UUID id,
      @Param("ownerId") UUID ownerId);
}
