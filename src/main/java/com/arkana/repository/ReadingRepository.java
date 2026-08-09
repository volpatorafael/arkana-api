package com.arkana.repository;

import com.arkana.domain.Reading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ReadingRepository extends JpaRepository<Reading, UUID>, JpaSpecificationExecutor<Reading> {
  Optional<Reading> findByIdAndOwnerId(UUID id, UUID ownerId);
}
