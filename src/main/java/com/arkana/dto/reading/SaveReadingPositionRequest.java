package com.arkana.dto.reading;

import jakarta.validation.constraints.Size;

public record SaveReadingPositionRequest(String cardId, String orientation, @Size(max = 10000) String interpretation) {
}
