package com.arkana.dto.reading;

import java.util.List;

public record ReadingPageResponse(
    List<ReadingSummaryResponse> items,
    int page,
    int pageSize,
    long total) {
}
