package com.arkana.mapper;

import com.arkana.domain.Profile;
import com.arkana.domain.ReadingComment;
import com.arkana.domain.ReadingShare;
import com.arkana.domain.Spread;
import com.arkana.dto.reading.ReadingShareResponse;
import com.arkana.dto.reading.SharedReadingResponse;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(injectionStrategy = InjectionStrategy.CONSTRUCTOR, uses = {
    SpreadMapper.class,
    ReadingPositionMapper.class,
    ReadingCommentMapper.class
})
public interface ReadingShareMapper {
  @Mapping(target = "readingId", source = "reading.id")
  ReadingShareResponse toResponse(ReadingShare share);

  @Mapping(target = "id", source = "share.id")
  @Mapping(target = "title", source = "share.reading.title")
  @Mapping(target = "question", source = "share.reading.question")
  @Mapping(target = "spread", source = "spread")
  @Mapping(target = "deckMode", source = "share.reading.deckMode")
  @Mapping(target = "analysisVideoUrl", source = "share.reading.analysisVideoUrl")
  @Mapping(target = "startedAt", source = "share.reading.startedAt")
  @Mapping(target = "completedAt", source = "share.reading.completedAt")
  @Mapping(target = "readerDisplayName", source = "reader.displayName")
  @Mapping(target = "positions", source = "positions")
  @Mapping(target = "comments", source = "comments")
  SharedReadingResponse toSharedResponse(
      ReadingShare share,
      Spread spread,
      Profile reader,
      List<ReadingPositionMappingSource> positions,
      List<ReadingComment> comments);
}
