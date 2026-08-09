package com.arkana.repository;

import com.arkana.domain.TarotCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TarotCardRepository extends JpaRepository<TarotCard, String> {
  List<TarotCard> findAllByOrderBySuitAscCardNumberAsc();

  List<TarotCard> findAllBySuitOrderByCardNumberAsc(String suit);
}
