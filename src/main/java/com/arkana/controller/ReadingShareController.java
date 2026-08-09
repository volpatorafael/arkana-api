package com.arkana.controller;

import com.arkana.dto.reading.ReadingShareResponse;
import com.arkana.security.CurrentUser;
import com.arkana.service.ReadingShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ReadingShareController {
  private final ReadingShareService readingShares;
  private final CurrentUser currentUser;

  @PostMapping("/readings/{readingId}/share")
  ReadingShareResponse create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID readingId) {
    return readingShares.create(currentUser.id(jwt), readingId);
  }

  @DeleteMapping("/readings/{readingId}/share")
  ResponseEntity<Void> cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID readingId) {
    readingShares.cancel(currentUser.id(jwt), readingId);
    return ResponseEntity.noContent().build();
  }
}
