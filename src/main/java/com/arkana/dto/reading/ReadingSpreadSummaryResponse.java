package com.arkana.dto.reading;

import com.arkana.domain.SpreadKind;

public record ReadingSpreadSummaryResponse(String id, String name, SpreadKind kind) {
}
