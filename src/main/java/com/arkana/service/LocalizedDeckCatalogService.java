package com.arkana.service;

import com.arkana.dto.catalog.DeckResponse;
import com.arkana.mapper.DeckMapper;
import com.arkana.repository.DeckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.arkana.config.CacheConfig.LOCALIZED_DECKS;

@Service
@RequiredArgsConstructor
public class LocalizedDeckCatalogService {
    private final DeckRepository decks;
    private final DeckMapper deckMapper;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = LOCALIZED_DECKS, key = "#locale", cacheManager = "deckCacheManager")
    public List<DeckResponse> decks(String locale) {
        return decks.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
            .map(deck -> deckMapper.toResponse(deck, locale))
            .toList();
    }
}
