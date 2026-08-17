package com.arkana.mapper;

import com.arkana.domain.ReadingPosition;
import com.arkana.domain.TarotCard;
import com.arkana.dto.reading.ReadingPositionResponse;
import com.arkana.dto.reading.SharedReadingPositionResponse;
import org.mapstruct.Context;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    injectionStrategy = InjectionStrategy.CONSTRUCTOR,
    uses = TarotCardMapper.class)
public interface ReadingPositionMapper {
  @Mapping(target = "key", source = "position.positionKey")
  @Mapping(target = "order", source = "position.positionOrder")
  @Mapping(target = "name", source = "position.namePtBr")
  @Mapping(target = "meaning", source = "position.meaningPtBr")
  @Mapping(target = "x", source = "position.x")
  @Mapping(target = "y", source = "position.y")
  @Mapping(target = "rotation", source = "position.rotation")
  @Mapping(target = "stackOrder", source = "position.stackOrder")
  @Mapping(target = "card", source = "card", qualifiedByName = "sharedSummary")
  @Mapping(target = "orientation", source = "position.orientation")
  @Mapping(target = "interpretation", source = "position.interpretation")
  SharedReadingPositionResponse toSharedResponse(ReadingPositionMappingSource source);

  @Mapping(target = "key", source = "position.positionKey")
  @Mapping(target = "id", source = "position.id")
  @Mapping(target = "order", source = "position.positionOrder")
  @Mapping(target = "name", expression = "java(name(position, locale))")
  @Mapping(target = "meaning", expression = "java(meaning(position, locale))")
  @Mapping(target = "x", source = "position.x")
  @Mapping(target = "y", source = "position.y")
  @Mapping(target = "rotation", source = "position.rotation")
  @Mapping(target = "stackOrder", source = "position.stackOrder")
  @Mapping(target = "card", source = "card")
  @Mapping(target = "orientation", source = "position.orientation")
  @Mapping(target = "interpretation", source = "position.interpretation")
  @Mapping(target = "createdAt", source = "position.createdAt")
  @Mapping(target = "updatedAt", source = "position.updatedAt")
  ReadingPositionResponse toResponse(
      ReadingPosition position,
      TarotCard card,
      @Context String locale);

  default String name(ReadingPosition position, String locale) {
    return "en".equals(locale) ? position.getNameEn() : position.getNamePtBr();
  }

  default String meaning(ReadingPosition position, String locale) {
    return "en".equals(locale) ? position.getMeaningEn() : position.getMeaningPtBr();
  }

}
