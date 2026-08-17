package com.arkana.dto.reading;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateFreeformReadingPositionLayoutRequest(
    @DecimalMin("0") @DecimalMax("100") BigDecimal x,
    @DecimalMin("0") @DecimalMax("100") BigDecimal y,
    @Min(-180) @Max(180) Short rotation) {

  public boolean any() {
    return x != null || y != null || rotation != null;
  }
}
