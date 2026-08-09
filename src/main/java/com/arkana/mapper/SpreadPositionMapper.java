package com.arkana.mapper;

import com.arkana.domain.SpreadPosition;
import com.arkana.dto.catalog.SpreadPositionResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface SpreadPositionMapper {
  @Mapping(target = "key", source = "positionKey")
  @Mapping(target = "order", source = "positionOrder")
  @Mapping(target = "name", expression = "java(name(position, locale))")
  @Mapping(target = "meaning", expression = "java(meaning(position, locale))")
  SpreadPositionResponse toResponse(SpreadPosition position, @Context String locale);

  default String name(SpreadPosition position, String locale) {
    return "en".equals(locale) ? position.getNameEn() : position.getNamePtBr();
  }

  default String meaning(SpreadPosition position, String locale) {
    return "en".equals(locale) ? position.getMeaningEn() : position.getMeaningPtBr();
  }
}
