package com.arkana.mapper;

import com.arkana.domain.Spread;
import com.arkana.dto.catalog.SpreadPositionResponse;
import com.arkana.dto.catalog.SpreadResponse;
import com.arkana.dto.reading.ReadingSpreadSummaryResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface SpreadMapper {
  @Mapping(target = "name", source = "namePtBr")
  @Mapping(target = "kind", source = "kind")
  ReadingSpreadSummaryResponse toSummaryResponse(Spread spread);

  @Mapping(target = "name", expression = "java(name(spread, locale))")
  @Mapping(target = "kind", source = "kind")
  ReadingSpreadSummaryResponse toSummaryResponse(Spread spread, @Context String locale);

  @Mapping(target = "id", source = "spread.id")
  @Mapping(target = "name", expression = "java(name(spread, locale))")
  @Mapping(target = "shortDescription", expression = "java(shortDescription(spread, locale))")
  @Mapping(target = "description", expression = "java(description(spread, locale))")
  @Mapping(target = "useCase", expression = "java(useCase(spread, locale))")
  @Mapping(target = "kind", source = "spread.kind")
  @Mapping(target = "positionCount", source = "spread.positionCount")
  @Mapping(target = "active", source = "spread.active")
  @Mapping(target = "positions", source = "positions")
  SpreadResponse toResponse(
      Spread spread,
      List<SpreadPositionResponse> positions,
      @Context String locale);

  default String name(Spread spread, String locale) {
    return "en".equals(locale) ? spread.getNameEn() : spread.getNamePtBr();
  }

  default String shortDescription(Spread spread, String locale) {
    return "en".equals(locale) ? spread.getShortDescriptionEn() : spread.getShortDescriptionPtBr();
  }

  default String description(Spread spread, String locale) {
    return "en".equals(locale) ? spread.getDescriptionEn() : spread.getDescriptionPtBr();
  }

  default String useCase(Spread spread, String locale) {
    return "en".equals(locale) ? spread.getUseCaseEn() : spread.getUseCasePtBr();
  }
}
