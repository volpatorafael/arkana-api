package com.arkana.repository;

import com.arkana.domain.Deck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeckRepository extends JpaRepository<Deck, String> {
  List<Deck> findAllByActiveTrueOrderByDisplayOrderAsc();
}
