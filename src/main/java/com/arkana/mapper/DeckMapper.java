package com.arkana.mapper;

import com.arkana.domain.Deck;
import com.arkana.dto.catalog.DeckResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface DeckMapper {
  @Mapping(target = "name", expression = "java(name(deck, locale))")
  DeckResponse toResponse(Deck deck, @Context String locale);

  default String name(Deck deck, String locale) {
    return "en".equals(locale) ? deck.getNameEn() : deck.getNamePtBr();
  }
}
