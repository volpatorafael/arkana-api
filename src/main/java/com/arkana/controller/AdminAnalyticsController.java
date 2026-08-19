package com.arkana.controller;

import com.arkana.dto.admin.AnalyticsOverviewResponse;
import com.arkana.dto.admin.IdentityAnalyticsOverviewResponse;
import com.arkana.service.AdminAnalyticsService;
import com.arkana.service.IdentityAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminAnalyticsController {
  private final AdminAnalyticsService analytics;
  private final IdentityAnalyticsService identityAnalytics;

  @GetMapping("/session")
  @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'SUPPORT', 'FINANCE')")
  ResponseEntity<Void> session() {
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/analytics/overview")
  @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
  AnalyticsOverviewResponse overview(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam String timeZone) {
    return analytics.overview(from, to, timeZone);
  }

  @GetMapping("/analytics/identity")
  @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
  IdentityAnalyticsOverviewResponse identity(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam String timeZone) {
    return identityAnalytics.overview(from, to, timeZone);
  }
}

