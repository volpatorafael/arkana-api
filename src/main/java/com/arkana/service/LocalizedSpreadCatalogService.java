package com.arkana.service;

import com.arkana.dto.catalog.SpreadResponse;
import com.arkana.mapper.SpreadMapper;
import com.arkana.mapper.SpreadPositionMapper;
import com.arkana.repository.SpreadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.arkana.config.CacheConfig.LOCALIZED_SPREADS;

@Service
@RequiredArgsConstructor
public class LocalizedSpreadCatalogService {
    private final SpreadRepository spreads;
    private final SpreadMapper spreadMapper;
    private final SpreadPositionMapper spreadPositionMapper;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = LOCALIZED_SPREADS, key = "#locale", cacheManager = "spreadCacheManager")
    public List<SpreadResponse> spreads(String locale) {
        return spreads.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
            .map(spread -> spreadMapper.toResponse(
                spread,
                spread.getPositions().stream()
                    .map(position -> spreadPositionMapper.toResponse(position, locale))
                    .toList(),
                locale))
            .toList();
    }
}
