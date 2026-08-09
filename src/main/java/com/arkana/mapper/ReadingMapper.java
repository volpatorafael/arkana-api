package com.arkana.mapper;

import com.arkana.domain.Reading;
import com.arkana.dto.reading.ReadingPageResponse;
import com.arkana.dto.reading.ReadingPositionResponse;
import com.arkana.dto.reading.ReadingResponse;
import com.arkana.dto.reading.ReadingSpreadSummaryResponse;
import com.arkana.dto.reading.ReadingSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ReadingMapper {
  @Mapping(target = "readingShareId", source = "readingShareId")
  ReadingSummaryResponse toSummary(Reading reading, UUID readingShareId);

  @Mapping(target = "readingShareId", source = "readingShareId")
  @Mapping(target = "id", source = "reading.id")
  @Mapping(target = "spread", source = "spread")
  @Mapping(target = "positions", source = "positions")
  ReadingResponse toResponse(
      Reading reading,
      UUID readingShareId,
      ReadingSpreadSummaryResponse spread,
      List<ReadingPositionResponse> positions);

  ReadingPageResponse toPage(
      List<ReadingSummaryResponse> items,
      int page,
      int pageSize,
      long total);
}
