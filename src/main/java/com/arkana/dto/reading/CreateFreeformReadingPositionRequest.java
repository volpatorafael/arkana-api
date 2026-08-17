package com.arkana.dto.reading;

import com.arkana.domain.CardOrientation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateFreeformReadingPositionRequest(
    @NotBlank String cardId,
    @NotNull CardOrientation orientation) {
}
