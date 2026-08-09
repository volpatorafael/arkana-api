package com.arkana.dto.reading;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class UpdateReadingRequest {
  private UUID clientId;
  private String spreadId, deckMode;
  @Size(max = 200)
  private String title;
  @Size(max = 5000)
  private String question;
  @Size(max = 10000)
  private String context;
  private boolean clientPresent, spreadPresent, deckPresent, titlePresent, questionPresent, contextPresent;

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
  public void setDeckMode(String value) {
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

  public UUID clientId() {
    return clientId;
  }

  public String spreadId() {
    return spreadId;
  }

  public String deckMode() {
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

  public boolean any() {
    return clientPresent || spreadPresent || deckPresent || titlePresent || questionPresent || contextPresent;
  }
}
