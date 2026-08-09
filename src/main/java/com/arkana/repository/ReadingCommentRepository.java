package com.arkana.repository;

import com.arkana.domain.ReadingComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingCommentRepository extends JpaRepository<ReadingComment, UUID> {
  List<ReadingComment> findAllByReadingIdAndOwnerIdOrderByCreatedAtAscIdAsc(UUID readingId, UUID ownerId);

  Optional<ReadingComment> findByIdAndReadingIdAndOwnerId(UUID id, UUID readingId, UUID ownerId);
}
