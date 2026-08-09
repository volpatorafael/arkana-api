package com.arkana.dto.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {
  @Size(max = 120, message = "displayName must contain at most 120 characters.")
  private String displayName;

  @Pattern(regexp = "pt-BR|en", message = "locale must be pt-BR or en.")
  private String locale;

  @JsonIgnore
  private boolean displayNamePresent;
  @JsonIgnore
  private boolean localePresent;

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayNamePresent = true;
    this.displayName = displayName;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.localePresent = true;
    this.locale = locale;
  }

  public boolean isDisplayNamePresent() {
    return displayNamePresent;
  }

  public boolean isLocalePresent() {
    return localePresent;
  }
}
