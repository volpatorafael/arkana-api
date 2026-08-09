package com.arkana.repository;

import com.arkana.domain.SpreadPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpreadPositionRepository extends JpaRepository<SpreadPosition, UUID> {
  List<SpreadPosition> findAllBySpread_IdOrderByPositionOrderAsc(String spreadId);
}
