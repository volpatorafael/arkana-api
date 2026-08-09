package com.arkana.controller;

import com.arkana.dto.reading.SharedReadingResponse;
import com.arkana.service.ReadingShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/v1/public/reading-shares")
@RequiredArgsConstructor
public class PublicReadingShareController {
  private final ReadingShareService readingShares;

  @GetMapping("/{readingShareId}")
  SharedReadingResponse get(@PathVariable UUID readingShareId) {
    return readingShares.publicReading(readingShareId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Reading share not found."));
  }
}
