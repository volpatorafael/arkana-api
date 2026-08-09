package com.arkana.dto.reading;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveReadingCommentRequest(@NotBlank @Size(max = 10000) String body) {
}
