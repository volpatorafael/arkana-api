package com.arkana.dto.reading;

import com.arkana.domain.CardOrientation;
import jakarta.validation.constraints.Size;

public record SaveReadingPositionRequest(
    String cardId,
    CardOrientation orientation,
    @Size(max = 10000) String interpretation) {
}
