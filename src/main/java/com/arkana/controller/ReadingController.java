package com.arkana.controller;

import com.arkana.dto.reading.CreateFreeformReadingPositionRequest;
import com.arkana.dto.reading.CreateReadingRequest;
import com.arkana.dto.reading.ReadingCommentResponse;
import com.arkana.dto.reading.ReadingPageResponse;
import com.arkana.dto.reading.ReadingPositionResponse;
import com.arkana.dto.reading.ReadingResponse;
import com.arkana.dto.reading.SaveReadingCommentRequest;
import com.arkana.dto.reading.SaveReadingPositionRequest;
import com.arkana.dto.reading.UpdateFreeformReadingPositionLayoutRequest;
import com.arkana.dto.reading.UpdateReadingRequest;
import com.arkana.security.CurrentUser;
import com.arkana.service.ReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/readings")
@RequiredArgsConstructor
public class ReadingController {
  private final ReadingService readings;
  private final CurrentUser currentUser;

  @GetMapping
  ReadingPageResponse list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "25") int pageSize,
      @RequestParam(defaultValue = "false") boolean archived,
      @RequestParam(required = false) UUID clientId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
      @RequestParam(required = false) String locale) {
    return readings.list(
        currentUser.id(jwt), page, pageSize, archived, clientId, status, from, to, locale);
  }

  @PostMapping
  ResponseEntity<ReadingResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateReadingRequest body,
      @RequestParam(required = false) String locale) {
    return ResponseEntity.status(201).body(readings.create(currentUser.id(jwt), body, locale));
  }

  @GetMapping("/{id}")
  ReadingResponse get(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(required = false) String locale) {
    return readings.get(currentUser.id(jwt), id, locale);
  }

  @PatchMapping("/{id}")
  ReadingResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateReadingRequest body,
      @RequestParam(required = false) String locale) {
    return readings.update(currentUser.id(jwt), id, body, locale);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    readings.delete(currentUser.id(jwt), id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/positions/{positionId}")
  ReadingPositionResponse position(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID positionId,
      @Valid @RequestBody SaveReadingPositionRequest body,
      @RequestParam(required = false) String locale) {
    return readings.savePosition(currentUser.id(jwt), id, positionId, body, locale);
  }

  @PostMapping("/{id}/positions")
  ResponseEntity<ReadingPositionResponse> createPosition(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody CreateFreeformReadingPositionRequest body,
      @RequestParam(required = false) String locale) {
    return ResponseEntity.status(201).body(
        readings.createFreeformPosition(currentUser.id(jwt), id, body, locale));
  }

  @PatchMapping("/{id}/positions/{positionId}")
  ReadingPositionResponse updatePositionLayout(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID positionId,
      @Valid @RequestBody UpdateFreeformReadingPositionLayoutRequest body,
      @RequestParam(required = false) String locale) {
    return readings.updateFreeformPositionLayout(
        currentUser.id(jwt), id, positionId, body, locale);
  }

  @DeleteMapping("/{id}/positions/{positionId}")
  ResponseEntity<Void> deletePosition(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID positionId) {
    readings.deleteFreeformPosition(currentUser.id(jwt), id, positionId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/complete")
  ReadingResponse complete(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(required = false) String locale) {
    return readings.complete(currentUser.id(jwt), id, locale);
  }

  @PostMapping("/{id}/archive")
  ReadingResponse archive(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(required = false) String locale) {
    return readings.archive(currentUser.id(jwt), id, locale);
  }

  @PostMapping("/{id}/restore")
  ReadingResponse restore(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(required = false) String locale) {
    return readings.restore(currentUser.id(jwt), id, locale);
  }

  @GetMapping("/{id}/comments")
  List<ReadingCommentResponse> comments(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id) {
    return readings.comments(currentUser.id(jwt), id);
  }

  @PostMapping("/{id}/comments")
  ResponseEntity<ReadingCommentResponse> addComment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody SaveReadingCommentRequest body) {
    return ResponseEntity.status(201).body(readings.addComment(currentUser.id(jwt), id, body));
  }

  @PatchMapping("/{id}/comments/{commentId}")
  ReadingCommentResponse updateComment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID commentId,
      @Valid @RequestBody SaveReadingCommentRequest body) {
    return readings.updateComment(currentUser.id(jwt), id, commentId, body);
  }

  @DeleteMapping("/{id}/comments/{commentId}")
  ResponseEntity<Void> deleteComment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID commentId) {
    readings.deleteComment(currentUser.id(jwt), id, commentId);
    return ResponseEntity.noContent().build();
  }
}
