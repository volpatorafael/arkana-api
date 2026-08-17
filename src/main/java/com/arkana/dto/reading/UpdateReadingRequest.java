package com.arkana.dto.reading;

import com.arkana.domain.ReadingDeckMode;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class UpdateReadingRequest {
  private UUID clientId;
  private String spreadId;
  private ReadingDeckMode deckMode;
  @Size(max = 200)
  private String title;
  @Size(max = 5000)
  private String question;
  @Size(max = 10000)
  private String context;
  @PositiveOrZero
  private Integer consultationFeeAmount;
  @Min(1)
  @Max(1440)
  private Integer consultationDurationMinutes;
  @Size(max = 2048)
  @Pattern(regexp = "^https://.+")
  private String analysisVideoUrl;
  private OffsetDateTime startedAt;
  private boolean clientPresent, spreadPresent, deckPresent, titlePresent, questionPresent, contextPresent,
      consultationFeeAmountPresent, consultationDurationMinutesPresent, analysisVideoUrlPresent,
      startedAtPresent;

  @JsonSetter
  public void setClientId(UUID value) {
    clientId = value;
    clientPresent = true;
  }

  @JsonSetter
  public void setSpreadId(String value) {
    spreadId = value;
    spreadPresent = true;
  }

  @JsonSetter
  public void setDeckMode(ReadingDeckMode value) {
    deckMode = value;
    deckPresent = true;
  }

  @JsonSetter
  public void setTitle(String value) {
    title = value;
    titlePresent = true;
  }

  @JsonSetter
  public void setQuestion(String value) {
    question = value;
    questionPresent = true;
  }

  @JsonSetter
  public void setContext(String value) {
    context = value;
    contextPresent = true;
  }

  @JsonSetter
  public void setConsultationFeeAmount(Integer value) {
    consultationFeeAmount = value;
    consultationFeeAmountPresent = true;
  }

  @JsonSetter
  public void setConsultationDurationMinutes(Integer value) {
    consultationDurationMinutes = value;
    consultationDurationMinutesPresent = true;
  }

  @JsonSetter
  public void setAnalysisVideoUrl(String value) {
    analysisVideoUrl = value;
    analysisVideoUrlPresent = true;
  }

  @JsonSetter
  public void setStartedAt(OffsetDateTime value) {
    startedAt = value;
    startedAtPresent = true;
  }

  public UUID clientId() {
    return clientId;
  }

  public String spreadId() {
    return spreadId;
  }

  public ReadingDeckMode deckMode() {
    return deckMode;
  }

  public String title() {
    return title;
  }

  public String question() {
    return question;
  }

  public String context() {
    return context;
  }

  public Integer consultationFeeAmount() {
    return consultationFeeAmount;
  }

  public Integer consultationDurationMinutes() {
    return consultationDurationMinutes;
  }

  public String analysisVideoUrl() {
    return analysisVideoUrl;
  }

  public OffsetDateTime startedAt() {
    return startedAt;
  }

  public boolean clientPresent() {
    return clientPresent;
  }

  public boolean spreadPresent() {
    return spreadPresent;
  }

  public boolean deckPresent() {
    return deckPresent;
  }

  public boolean titlePresent() {
    return titlePresent;
  }

  public boolean questionPresent() {
    return questionPresent;
  }

  public boolean contextPresent() {
    return contextPresent;
  }

  public boolean consultationFeeAmountPresent() {
    return consultationFeeAmountPresent;
  }

  public boolean consultationDurationMinutesPresent() {
    return consultationDurationMinutesPresent;
  }

  public boolean analysisVideoUrlPresent() {
    return analysisVideoUrlPresent;
  }

  public boolean startedAtPresent() {
    return startedAtPresent;
  }

  public boolean any() {
    return clientPresent || spreadPresent || deckPresent || titlePresent || questionPresent || contextPresent
        || consultationFeeAmountPresent || consultationDurationMinutesPresent || analysisVideoUrlPresent
        || startedAtPresent;
  }
}
