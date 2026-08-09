package com.arkana.controller;

import com.arkana.dto.billing.SubscriptionPlanResponse;
import com.arkana.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/public/plans")
@RequiredArgsConstructor
public class BillingPlansController {
  private final BillingService billing;

  @GetMapping
  ResponseEntity<List<SubscriptionPlanResponse>> list() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60, s-maxage=60, stale-while-revalidate=60")
        .body(billing.plans());
  }
}
