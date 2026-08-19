package com.arkana.mapper;

import com.arkana.domain.Deck;
import com.arkana.domain.TarotCard;
import com.arkana.dto.admin.AdminDeckResponse;
import com.arkana.dto.admin.AdminTarotCardResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AdminCatalogMapper {
  AdminDeckResponse toAdminDeck(Deck deck);

  @Mapping(target = "number", source = "cardNumber")
  AdminTarotCardResponse toAdminCard(TarotCard card);
}
