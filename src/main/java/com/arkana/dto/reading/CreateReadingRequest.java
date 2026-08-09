package com.arkana.dto.reading;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReadingRequest(UUID clientId, @NotBlank String spreadId, @NotBlank String deckMode,
                                   @Size(max = 200) String title, @Size(max = 5000) String question,
                                   @Size(max = 10000) String context) {
}
