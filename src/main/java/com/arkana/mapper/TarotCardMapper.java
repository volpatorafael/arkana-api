package com.arkana.mapper;

import com.arkana.domain.TarotCard;
import com.arkana.dto.catalog.TarotCardResponse;
import com.arkana.dto.reading.TarotCardSummaryResponse;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper
public interface TarotCardMapper {
  @Named("sharedSummary")
  @Mapping(target = "number", source = "cardNumber")
  @Mapping(target = "name", source = "namePtBr")
  TarotCardSummaryResponse toSharedSummary(TarotCard card);

  @Mapping(target = "number", source = "cardNumber")
  @Mapping(target = "name", expression = "java(name(card, locale))")
  TarotCardSummaryResponse toSummaryResponse(TarotCard card, @Context String locale);

  @Mapping(target = "number", source = "cardNumber")
  @Mapping(target = "name", expression = "java(name(card, locale))")
  @Mapping(target = "description", expression = "java(description(card, locale))")
  @Mapping(target = "lightMeaning", expression = "java(lightMeaning(card, locale))")
  @Mapping(target = "shadowMeaning", expression = "java(shadowMeaning(card, locale))")
  TarotCardResponse toResponse(TarotCard card, @Context String locale);

  default String name(TarotCard card, String locale) {
    return "en".equals(locale) ? card.getNameEn() : card.getNamePtBr();
  }

  default String description(TarotCard card, String locale) {
    return "en".equals(locale) ? card.getDescriptionEn() : card.getDescriptionPtBr();
  }

  default String lightMeaning(TarotCard card, String locale) {
    return "en".equals(locale) ? card.getLightEn() : card.getLightPtBr();
  }

  default String shadowMeaning(TarotCard card, String locale) {
    return "en".equals(locale) ? card.getShadowEn() : card.getShadowPtBr();
  }
}
