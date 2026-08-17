package com.arkana.dto.reading;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// TODO(multi-deck): deckId is optional while the frontend doesn't send it yet
// (Fase 4a/4b compatibility window). Once the deck selector ships, make it
// @NotBlank like spreadId and deckMode.
public record CreateReadingRequest(UUID clientId, @NotBlank String spreadId, String deckId,
                                   @NotBlank String deckMode,
                                   @Size(max = 200) String title, @Size(max = 5000) String question,
                                   @Size(max = 10000) String context,
                                   @PositiveOrZero Integer consultationFeeAmount,
                                   @Min(1) @Max(1440) Integer consultationDurationMinutes,
                                   @Size(max = 2048) @Pattern(regexp = "^https://.+") String analysisVideoUrl) {
}
