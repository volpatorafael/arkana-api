package com.arkana.mapper;

import com.arkana.domain.ReadingComment;
import com.arkana.dto.reading.ReadingCommentResponse;
import com.arkana.dto.reading.SharedReadingCommentResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface ReadingCommentMapper {
  SharedReadingCommentResponse toSharedResponse(ReadingComment comment);

  List<SharedReadingCommentResponse> toSharedResponses(List<ReadingComment> comments);

  ReadingCommentResponse toResponse(ReadingComment comment);

  List<ReadingCommentResponse> toResponses(List<ReadingComment> comments);
}
