package com.arkana.repository;

import com.arkana.domain.Spread;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpreadRepository extends JpaRepository<Spread, String> {
  @EntityGraph(attributePaths = "positions")
  List<Spread> findAllByActiveTrueOrderByDisplayOrderAsc();

  @EntityGraph(attributePaths = "positions")
  Optional<Spread> findByIdAndActiveTrue(String id);
}
