package com.arkana.repository;

import com.arkana.domain.ReadingPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingPositionRepository extends JpaRepository<ReadingPosition, UUID> {
  List<ReadingPosition> findAllByReadingIdOrderByPositionOrderAsc(UUID readingId);

  Optional<ReadingPosition> findByIdAndReadingId(UUID id, UUID readingId);

  long countByReadingIdAndCardIdIsNull(UUID readingId);

  boolean existsByReadingIdAndCardIdAndIdNot(UUID readingId, String cardId, UUID id);

  boolean existsByReadingIdAndCardId(UUID readingId, String cardId);

  boolean existsByCardId(String cardId);

  void deleteAllByReadingId(UUID readingId);
}
